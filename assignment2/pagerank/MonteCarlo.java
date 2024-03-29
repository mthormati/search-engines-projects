import java.util.*;
import java.io.*;

public class MonteCarlo {
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

    final static int M = 1;

    /* --------------------------------------------- */

    public MonteCarlo( String filename ) {
        int noOfDocs = readDocs( filename );
        // MonteCarlo1( noOfDocs );
        // MonteCarlo2( noOfDocs );
        MonteCarlo4( noOfDocs );
        // MonteCarlo5( noOfDocs );

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


    public void MonteCarlo1( int numberOfDocs ) {
        long start = System.currentTimeMillis();
        final int N = numberOfDocs * M;
        double[] x = new double[numberOfDocs];
        Random random = new Random();

        int walk;
        for (int i = 0; i < N; i++) {
            int currKey = random.nextInt(numberOfDocs);

            while ((walk = random.nextInt(100)) < 85) {
                if (out[currKey] == 0) currKey = random.nextInt(numberOfDocs);
                else {
                    List<Integer> outLinks = new ArrayList<Integer>(link.get(currKey).keySet());
                    currKey = outLinks.get(random.nextInt(outLinks.size()));
                }
            }
            x[currKey]++;
        }

        for (int i = 0; i < x.length; i++) {
            x[i] = x[i] / N;
        }
        long elapsedTime = System.currentTimeMillis() - start;
        System.err.println("Elapsed Time: " + (double)elapsedTime / 1000 + " sec");

        System.err.println("Monte Carlo 1 SS: " + String.format("%.10f", sumOfSquares(x)));
        System.err.println();
    }

    public void MonteCarlo2( int numberOfDocs ) {
        long start = System.currentTimeMillis();
        double[] x = new double[numberOfDocs];
        Random random = new Random();

        int startingKey = 0;
        int count = 0;
        int walk;
        while (startingKey < numberOfDocs) {
            int currKey = startingKey;

            while ((walk = random.nextInt(100)) < 85) {
                if (out[currKey] == 0) currKey = random.nextInt(numberOfDocs);
                else {
                    List<Integer> outLinks = new ArrayList<Integer>(link.get(currKey).keySet());
                    currKey = outLinks.get(random.nextInt(outLinks.size()));
                }
            }
            x[currKey]++;
            count++;
            if (count >= M) {
                 startingKey++; 
                 count = 0;
            }
        }

        for (int i = 0; i < x.length; i++) {
            x[i] = x[i] / (numberOfDocs * M);
        }
        long elapsedTime = System.currentTimeMillis() - start;
        System.err.println("Elapsed Time: " + (double)elapsedTime / 1000 + " sec");

        System.err.println("Monte Carlo 2 SS: " + String.format("%.10f", sumOfSquares(x)));
        System.err.println();
    }

    public void MonteCarlo4( int numberOfDocs ) {
        // long start = System.currentTimeMillis();
        double[] x = new double[numberOfDocs];
        Random random = new Random();
        int totalVisits = 0;

        int startingKey = 0;
        int count = 0;
        int walk;
        while (startingKey < numberOfDocs) {
            int currKey = startingKey; 
            x[currKey]++; totalVisits++;

            while ((walk = random.nextInt(100)) < 85 && out[currKey] != 0) {
                List<Integer> outLinks = new ArrayList<Integer>(link.get(currKey).keySet());
                currKey = outLinks.get(random.nextInt(outLinks.size()));
                x[currKey]++; totalVisits++; 
            }

            count++;
            if (count >= M) {
                 startingKey++; 
                 count = 0;
            }
        }

        for (int i = 0; i < x.length; i++) {
            x[i] = x[i] / totalVisits;
        }
        printPageRanks(x);
        // long elapsedTime = System.currentTimeMillis() - start;
        // System.err.println("Elapsed Time: " + (double)elapsedTime / 1000 + " sec");

        // System.err.println("Monte Carlo 4 SS: " + String.format("%.10f", sumOfSquares(x)));
        // System.err.println();
    }

    public void MonteCarlo5( int numberOfDocs ) {
        long start = System.currentTimeMillis();
        int N = numberOfDocs * M;
        double[] x = new double[numberOfDocs];
        Random random = new Random();

        int totalVisits = 0;
        int walk;
        for (int i = 0; i < N; i++) {
            int currKey = random.nextInt(numberOfDocs);
            x[currKey]++; totalVisits++;

            while ((walk = random.nextInt(100)) < 85 && out[currKey] != 0) {
                List<Integer> outLinks = new ArrayList<Integer>(link.get(currKey).keySet());
                currKey = outLinks.get(random.nextInt(outLinks.size()));
                x[currKey]++; totalVisits++; 
            }
        }

        for (int i = 0; i < x.length; i++) {
            x[i] = x[i] / totalVisits;
        }
        long elapsedTime = System.currentTimeMillis() - start;
        // System.err.println("Elapsed Time: " + (double)elapsedTime / 1000 + " sec");

        // System.err.println("Monte Carlo 5 SS: " + String.format("%.10f", sumOfSquares(x)));
        // System.err.println(); 
        printPageRanks(x);   
    }
    

    /* --------------------------------------------- */

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

    private double sumOfSquares( double[] x ) {
        double sumOfSquares = 0;
        try {
            BufferedReader in = new BufferedReader( new FileReader( "davis_top_30.txt" ));
            String line;

            while ((line = in.readLine()) != null) {
                int split = line.indexOf(":");
                String title = line.substring(0, split);
                double value = Double.parseDouble(line.substring(split + 2));

                sumOfSquares += Math.pow(x[docNumber.get(title)] - value, 2);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }

        return sumOfSquares;
    }

    /* --------------------------------------------- */

    public static void main( String[] args ) {
        if ( args.length != 1 ) {
            System.err.println( "Please give the name of the link file" );
        }
        else {
            new MonteCarlo( args[0] );
        }
    }
}