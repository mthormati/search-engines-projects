/**
 *   Computes the Hubs and Authorities for an every document in a query-specific
 *   link graph, induced by the base set of pages.
 *
 *   @author Dmytro Kalpakchi
 */

package ir;

import java.util.*;
import java.io.*;


public class HITSRanker {

    /**
     *   Max number of iterations for HITS
     */
    final static int MAX_NUMBER_OF_STEPS = 1000;

    /**  
     *   Maximal number of documents. We're assuming here that we
     *   don't have more docs than we can keep in main memory.
     */
    final static int MAX_NUMBER_OF_DOCS = 200000;

    /**
     *   Convergence criterion: hub and authority scores do not 
     *   change more that EPSILON from one iteration to another.
     */
    final static double EPSILON = 0.001;

    /**
     *   The inverted index
     */
    Index index;

    /**
     *   Mapping from the titles to internal document ids used in the links file
     */
    HashMap<String,Integer> titleToId = new HashMap<String,Integer>();
    HashMap<Integer,String> IdToTitle = new HashMap<Integer,String>();

    /**
     *   Mapping from document names to document numbers.
     */
    HashMap<String,Integer> docNumber = new HashMap<String,Integer>();

    /**
     *   Mapping from document numbers to document names
     */
    String[] docName = new String[MAX_NUMBER_OF_DOCS];

    /**
     *   Sparse vector containing hub scores
     */
    HashMap<Integer,Double> hubs = new HashMap<Integer, Double>();

    /**
     *   Sparse vector containing authority scores
     */
    HashMap<Integer,Double> authorities = new HashMap<Integer, Double>();

    HashMap<Integer,HashMap<Integer,Boolean>> link = new HashMap<Integer,HashMap<Integer,Boolean>>();

    int[] out = new int[MAX_NUMBER_OF_DOCS];

    PostingsList queryList;

    int nDocs;
    
    /* --------------------------------------------- */

    /**
     * Constructs the HITSRanker object
     * 
     * A set of linked documents can be presented as a graph.
     * Each page is a node in graph with a distinct nodeID associated with it.
     * There is an edge between two nodes if there is a link between two pages.
     * 
     * Each line in the links file has the following format:
     *  nodeID;outNodeID1,outNodeID2,...,outNodeIDK
     * This means that there are edges between nodeID and outNodeIDi, where i is between 1 and K.
     * 
     * Each line in the titles file has the following format:
     *  nodeID;pageTitle
     *  
     * NOTE: nodeIDs are consistent between these two files, but they are NOT the same
     *       as docIDs used by search engine's Indexer
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     * @param      index           The inverted index
     */
    public HITSRanker( String linksFilename, String titlesFilename, Index index, PostingsList queryList ) {
        this.index = index;
        this.queryList = queryList;
        this.index = index;
        if (this.queryList == null) {
            int noOfDocs = readDocs( linksFilename, titlesFilename );
            rank(noOfDocs);
        } else {
            nDocs = readListDocs( linksFilename, titlesFilename, queryList );
        }
    }


    /* --------------------------------------------- */

    /**
     * A utility function that gets a file name given its path.
     * For example, given the path "davisWiki/hello.f",
     * the function will return "hello.f".
     *
     * @param      path  The file path
     *
     * @return     The file name.
     */
    private String getFileName( String path ) {
        String result = "";
        StringTokenizer tok = new StringTokenizer( path, "\\/" );
        while ( tok.hasMoreTokens() ) {
            result = tok.nextToken();
        }
        return result;
    }

    /**
     * Reads the files describing the graph of the given set of pages.
     *
     * @param      linksFilename   File containing the links of the graph
     * @param      titlesFilename  File containing the mapping between nodeIDs and pages titles
     */
    int readDocs( String linksFilename, String titlesFilename ) {
        int fileIndex = 0;
        try {
            System.err.print( "Reading file... " );
            BufferedReader in = new BufferedReader( new FileReader( linksFilename ));
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

            in = new BufferedReader( new FileReader( titlesFilename ) );
            while ((line = in.readLine()) != null) {
                int index = line.indexOf(";");
                int docID = Integer.parseInt(line.substring(0, index));
                String title = line.substring(index + 1);
                titleToId.put(title, docID);
            }
        }
        catch ( IOException e ) {
            System.err.println( "Error reading file" );
        }
        System.err.println( "Read " + fileIndex + " number of documents" );
        return fileIndex;
    }

    private int realIDtoInnerID( int id ) {
        if (titleToId.get(getFileName(index.docNames.get(id))) == null) System.err.println("It be that way sometimes");
        return titleToId.get(getFileName(index.docNames.get(id)));
    }

    private Integer innerIDToRealID( int id ) {
        String path = "./davisWiki/" + IdToTitle.get(id);
        return index.docIDs.get(path);
    }

    int readListDocs( String linksFilename, String titlesFilename, PostingsList list ) {
        try {
            BufferedReader in = new BufferedReader( new FileReader( titlesFilename ) );
            String line;
            while ((line = in.readLine()) != null) {
                int index = line.indexOf(";");
                int docID = Integer.parseInt(line.substring(0, index));
                String title = line.substring(index + 1);
                titleToId.put(title, docID);
                IdToTitle.put(docID, title);
            }
        }
        catch ( IOException e ) {
            System.err.println( "Error reading file" );
        }

        HashSet<Integer> sigID = new HashSet<Integer>();
        for (int i = 0; i < list.size(); i++) {
            sigID.add(realIDtoInnerID(list.get(i).docID));
        }

        int fileIndex = 0;
        try {
            System.err.print( "Reading file... " );
            BufferedReader in = new BufferedReader( new FileReader( linksFilename ));
            String line;
            while ((line = in.readLine()) != null && fileIndex<MAX_NUMBER_OF_DOCS ) {
                int index = line.indexOf( ";" );
                String title = line.substring( 0, index );
                Integer fromdoc = docNumber.get( title );

                if (sigID.contains(Integer.parseInt(title))) {
                    //  Have we seen this document before?
                    if ( fromdoc == null ) {	
                        // This is a previously unseen doc, so add it to the table.
                        fromdoc = fileIndex++;
                        docNumber.put( title, fromdoc );
                        docName[fromdoc] = title;
                    }
                }

                // Check all outlinks.
                StringTokenizer tok = new StringTokenizer( line.substring(index+1), "," );
                while ( tok.hasMoreTokens() && fileIndex<MAX_NUMBER_OF_DOCS ) {
                    String otherTitle = tok.nextToken();
                    Integer otherDoc = docNumber.get( otherTitle );

                    if ( !sigID.contains(Integer.parseInt(title)) && !sigID.contains(Integer.parseInt(otherTitle)) ) continue;

                    //  Have we seen this document before?
                    if ( fromdoc == null ) {	
                        // This is a previously unseen doc, so add it to the table.
                        fromdoc = fileIndex++;
                        docNumber.put( title, fromdoc );
                        docName[fromdoc] = title;
                    }

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
        }
        catch ( IOException e ) {
            System.err.println( "Error reading file" );
        }
        System.err.println( "Read " + fileIndex + " number of documents" );
        return fileIndex;
    }

    /**
     * Perform HITS iterations until convergence
     *
     */
    private void iterate(int noOfDocs) {
        double[] x = new double[noOfDocs];
        double[] xPrime = new double[noOfDocs]; Arrays.fill(xPrime, 1);

        double[] y = new double[noOfDocs];
        double[] yPrime = new double[noOfDocs]; Arrays.fill(yPrime, 1);

        int step = 0;
        while (!diffLessThanEpsilon(x, xPrime, y, yPrime) && step < MAX_NUMBER_OF_STEPS) {
            step++;
            x = xPrime; y = yPrime;
            xPrime = multiplyWithTransition(y, true);
            yPrime = multiplyWithTransition(x, false);
            xPrime = normalize(xPrime);
            yPrime = normalize(yPrime);
        }

        for (int i = 0; i < noOfDocs; i++) {
            authorities.put(Integer.parseInt(docName[i]), yPrime[i]);
            hubs.put(Integer.parseInt(docName[i]), xPrime[i]);
        }
    }

	private boolean diffLessThanEpsilon( double[] x, double[] xPrime, double[] y, double[] yPrime ) {
        for (int i = 0; i < x.length; i++) {
            if (Math.abs(x[i] - xPrime[i]) > EPSILON ||
                Math.abs(y[i] - yPrime[i]) > EPSILON) return false;
        }
        return true;
    }
    
	private double[] multiplyWithTransition( double[] xPrime, boolean transpose ) {
		double[] result = new double[xPrime.length];

        for (int i = 0; i < xPrime.length; i++) {
            double indexValue = 0;
            for (int j = 0; j < xPrime.length; j++) {
                if (transpose) indexValue += calcTransitionVal(i, j) * xPrime[j];
                else           indexValue += calcTransitionVal(j, i) * xPrime[j];
            }
            result[i] = indexValue;
        }

        return result;
    }
    
	private int calcTransitionVal( int i, int j ) {		
        if (out[i] == 0) return 0;
		Boolean val = link.get(i).get(j);
		if (val == null) return 0;
		else 			 return 1;
	}

    private double[] normalize( double[] vec ) {
        double[] res = new double[vec.length];
        //Calculate length
        double length = 0;
        for (int i = 0; i < vec.length; i++) {
            length += Math.pow(vec[i], 2);
        }
        length = Math.sqrt(length);
        //Normalize
        for (int i = 0; i < vec.length; i++) {
            res[i] = vec[i] / length;
        }
        return res;
    }

    /**
     * Rank the documents in the subgraph induced by the documents present
     * in the postings list `post`.
     *
     * @param      post  The list of postings fulfilling a certain information need
     *
     * @return     A list of postings ranked according to the hub and authority scores.
     */
    PostingsList rank() {
        iterate(nDocs);
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);

        for (Map.Entry<Integer,Double> entry : sortedHubs.entrySet()) {
            entry.setValue(entry.getValue() + sortedAuthorities.get(entry.getKey()));
        }
        sortedHubs = sortHashMapByValue(sortedHubs);

        PostingsList newList = new PostingsList();
        for (Map.Entry<Integer,Double> entry : sortedHubs.entrySet()) {
            Integer realID = innerIDToRealID(entry.getKey());
            if (realID == null) continue;
            newList.add(realID, 0);
            newList.setScore(realID, entry.getValue());
        }

        return newList;
    }


    /**
     * Sort a hash map by values in the descending order
     *
     * @param      map    A hash map to sorted
     *
     * @return     A hash map sorted by values
     */
    private HashMap<Integer,Double> sortHashMapByValue(HashMap<Integer,Double> map) {
        if (map == null) {
            return null;
        } else {
            List<Map.Entry<Integer,Double> > list = new ArrayList<Map.Entry<Integer,Double> >(map.entrySet());
      
            Collections.sort(list, new Comparator<Map.Entry<Integer,Double>>() {
                public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) { 
                    return (o2.getValue()).compareTo(o1.getValue()); 
                } 
            }); 
              
            HashMap<Integer,Double> res = new LinkedHashMap<Integer,Double>(); 
            for (Map.Entry<Integer,Double> el : list) { 
                res.put(el.getKey(), el.getValue()); 
            }
            return res;
        }
    } 


    /**
     * Write the first `k` entries of a hash map `map` to the file `fname`.
     *
     * @param      map        A hash map
     * @param      fname      The filename
     * @param      k          A number of entries to write
     */
    void writeToFile(HashMap<Integer,Double> map, String fname, int k) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(fname));
            
            if (map != null) {
                int i = 0;
                for (Map.Entry<Integer,Double> e : map.entrySet()) {
                    i++;
                    writer.write(e.getKey() + ": " + String.format("%.5g%n", e.getValue()));
                    if (i >= k) break;
                }
            }
            writer.close();
        } catch (IOException e) {}
    }


    /**
     * Rank all the documents in the links file. Produces two files:
     *  hubs_top_30.txt with documents containing top 30 hub scores
     *  authorities_top_30.txt with documents containing top 30 authority scores
     */
    void rank( int noOfDocs ) {
        iterate(noOfDocs);
        HashMap<Integer,Double> sortedHubs = sortHashMapByValue(hubs);
        HashMap<Integer,Double> sortedAuthorities = sortHashMapByValue(authorities);
        writeToFile(sortedHubs, "hubs_top_30.txt", 30);
        writeToFile(sortedAuthorities, "authorities_top_30.txt", 30);
    }


    /* --------------------------------------------- */


    public static void main( String[] args ) {
        if ( args.length != 2 ) {
            System.err.println( "Please give the names of the link and title files" );
        }
        else {
            HITSRanker hr = new HITSRanker( args[0], args[1], null, null );
            // hr.rank();
        }
    }
} 