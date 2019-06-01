import java.util.*;
import java.io.*;

public class PageRankSparse {

    /**  
     *   Maximal number of documents. We're assuming here that we
     *   don't have more docs than we can keep in main memory.
     */
    final static int MAX_NUMBER_OF_DOCS = 2000000;

    /**
     *   Mapping from document names to document numbers.
     */
    HashMap<String,Integer> docNumber = new HashMap<String,Integer>();

    /**
     *   Mapping from document numbers to document names
     */
    String[] docName = new String[MAX_NUMBER_OF_DOCS];

    /**  
     *   A memory-efficient representation of the transition matrix.
     *   The outlinks are represented as a HashMap, whose keys are 
     *   the numbers of the documents linked from.<p>
     *
     *   The value corresponding to key i is a HashMap whose keys are 
     *   all the numbers of documents j that i links to.<p>
     *
     *   If there are no outlinks from i, then the value corresponding 
     *   key i is null.
     */
    HashMap<Integer,HashMap<Integer,Boolean>> link = new HashMap<Integer,HashMap<Integer,Boolean>>();

    /**
     *   The number of outlinks from each node.
     */
    int[] out = new int[MAX_NUMBER_OF_DOCS];

    /**
     *   The probability that the surfer will be bored, stop
     *   following links, and take a random jump somewhere.
     */
    final static double BORED = 0.15;

    /**
     *   Convergence criterion: Transition probabilities do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.0001;

       
    /* --------------------------------------------- */


    public PageRankSparse( String filename ) {
	int noOfDocs = readDocs( filename );
	iterate( noOfDocs, 1000 );
    }


    /* --------------------------------------------- */


    /**
     *   Reads the documents and fills the data structures. 
     *
     *   @return the number of documents read.
     */
    int readDocs( String filename ) {
	int fileIndex = 0;
	try {
	    System.err.print( "Reading file... " );
	    BufferedReader in = new BufferedReader( new FileReader( filename ));
	    String line;
	    while ((line = in.readLine()) != null && fileIndex<MAX_NUMBER_OF_DOCS ) {
		int index = line.indexOf( ";" );
		String title = line.substring( 0, index );
		Integer fromdoc = docNumber.get( title );
		//  Have we seen this document before?
		if ( fromdoc == null ) {	
		    // This is a previously unseen doc, so add it to the table.
		    fromdoc = fileIndex++;
		    docNumber.put( title, fromdoc );
		    docName[fromdoc] = title;
		}
		// Check all outlinks.
		StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
		while ( tok.hasMoreTokens() && fileIndex<MAX_NUMBER_OF_DOCS ) {
		    String otherTitle = tok.nextToken();
		    Integer otherDoc = docNumber.get( otherTitle );
		    if ( otherDoc == null ) {
			// This is a previousy unseen doc, so add it to the table.
			otherDoc = fileIndex++;
			docNumber.put( otherTitle, otherDoc );
			docName[otherDoc] = otherTitle;
		    }
		    // Set the probability to 0 for now, to indicate that there is
		    // a link from fromdoc to otherDoc.
		    if ( link.get(fromdoc) == null ) {
			link.put(fromdoc, new HashMap<Integer,Boolean>());
		    }
		    if ( link.get(fromdoc).get(otherDoc) == null ) {
			link.get(fromdoc).put( otherDoc, true );
			out[fromdoc]++;
		    }
		}
	    }
	    if ( fileIndex >= MAX_NUMBER_OF_DOCS ) {
		System.err.print( "stopped reading since documents table is full. " );
	    }
	    else {
		System.err.print( "done. " );
	    }
	}
	catch ( FileNotFoundException e ) {
	    System.err.println( "File " + filename + " not found!" );
	}
	catch ( IOException e ) {
	    System.err.println( "Error reading file " + filename );
	}
	System.err.println( "Read " + fileIndex + " number of documents" );
	return fileIndex;
    }


    /* --------------------------------------------- */


    /*
     *   Chooses a probability vector a, and repeatedly computes
     *   aP, aP^2, aP^3... until aP^i = aP^(i+1).
     */
    void iterate( int numberOfDocs, int maxIterations ) {
        double[] x = new double[numberOfDocs];
        double[] xPrime = new double[numberOfDocs]; xPrime[0] = 1;
        int numIteration = 0;
        while (!diffLessThanEpsilon(x, xPrime) && numIteration < maxIterations) {
            numIteration++;
			x = xPrime;
            xPrime = multiplyWithTransition(x);
        }
		printPageRanks(xPrime);		
	}
	
	private boolean diffLessThanEpsilon( double[] x, double[] xPrime ) {
        for (int i = 0; i < x.length; i++) {
            if (Math.abs(x[i] - xPrime[i]) > EPSILON) return false;
        }
        return true;
	}
	
	private double[] multiplyWithTransition( double[] xPrime ) {
		double[] result = new double[xPrime.length];
		double p1 = 1 / (double)xPrime.length;
		double p2 = BORED * p1;

        for (int i = 0; i < xPrime.length; i++) {
            double indexValue = 0;
            for (int j = 0; j < xPrime.length; j++) {
                indexValue += calcTransitionVal(j, i, xPrime.length, p1, p2) * xPrime[j];
            }
            result[i] = indexValue;
        }

        return result;
	}
	
	private double calcTransitionVal( int i, int j, int numberOfDocs, 
									  double p1, double p2 ) {		
		if (out[i] == 0) return p1;
		Boolean val = link.get(i).get(j);
		if (val == null) return p2;
		else 			 return ((1 - BORED) * (1 / (double)out[i])) + p2;
	}

    private void printPageRanks( double[] xPrime ) {
        HashMap<Integer, Double> map = new HashMap<Integer, Double>();
        for (int i = 0; i < xPrime.length; i++) {
            map.put(i, xPrime[i]);
        }

        Object[] a = map.entrySet().toArray();
        Arrays.sort(a, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Map.Entry<Integer, Double>) o2).getValue()
                           .compareTo(((Map.Entry<Integer, Double>) o1).getValue());
            }
        });
        int count = 0;
        for (Object e : a) {
            if (count >= 30) break;
            System.out.println(docName[((Map.Entry<Integer, Double>) e).getKey()] + ": "
                    + String.format("%.5f", ((Map.Entry<Integer, Double>) e).getValue()));
            count++;
        }
    }

    /* --------------------------------------------- */

    public static void main( String[] args ) {
	if ( args.length != 1 ) {
	    System.err.println( "Please give the name of the link file" );
	}
	else {
	    new PageRankSparse( args[0] );
	}
    }
}