/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.nio.charset.*;
import java.io.*;


/**
 *  A class for representing a query as a list of words, each of which has
 *  an associated weight.
 */
public class Query {

    /**
     *  Help class to represent one query term, with its associated weight. 
     */
    class QueryTerm {
        String term;
        double weight;
        QueryTerm( String t, double w ) {
            term = t;
            weight = w;
        }
    }

    /** 
     *  Representation of the query as a list of terms with associated weights.
     *  In assignments 1 and 2, the weight of each term will always be 1.
     */
    public ArrayList<QueryTerm> queryterm = new ArrayList<QueryTerm>();

    /**  
     *  Relevance feedback constant alpha (= weight of original query terms). 
     *  Should be between 0 and 1.
     *  (only used in assignment 3).
     */
    double alpha = 0.2;

    /**  
     *  Relevance feedback constant beta (= weight of query terms obtained by
     *  feedback from the user). 
     *  (only used in assignment 3).
     */
    double beta = 1 - alpha;
    
    
    /**
     *  Creates a new empty Query 
     */
    public Query() {
    }
    
    
    /**
     *  Creates a new Query from a string of words
     */
    public Query( String queryString  ) {
        StringTokenizer tok = new StringTokenizer( queryString );
        while ( tok.hasMoreTokens() ) {
            queryterm.add( new QueryTerm(tok.nextToken(), 1.0) );
        }    
    }
    
    
    /**
     *  Returns the number of terms
     */
    public int size() {
        return queryterm.size();
    }
    
    
    /**
     *  Returns the Manhattan query length
     */
    public double length() {
        double len = 0;
        for ( QueryTerm t : queryterm ) {
            len += t.weight; 
        }
        return len;
    }
    
    
    /**
     *  Returns a copy of the Query
     */
    public Query copy() {
        Query queryCopy = new Query();
        for ( QueryTerm t : queryterm ) {
            queryCopy.queryterm.add( new QueryTerm(t.term, t.weight) );
        }
        return queryCopy;
    }
    
    
    /**
     *  Expands the Query using Relevance Feedback
     *
     *  @param results The results of the previous query.
     *  @param docIsRelevant A boolean array representing which query results the user deemed relevant.
     *  @param engine The search engine object
     */
    public void relevanceFeedback( PostingsList results, boolean[] docIsRelevant, Engine engine ) {
        HashMap<String, Double> terms = new HashMap<String, Double>();
        //Normalize queryterm weights
        for (QueryTerm t : queryterm) {
            t.weight /= length();
        }
        //Multiply original term vector with alpha
        for (QueryTerm t : queryterm) {
            t.weight = t.weight * alpha;
            terms.put(t.term, t.weight);
        }
        //Weight terms of relevant documents
        for (int i = 0; i < docIsRelevant.length; i++) {
            if (docIsRelevant[i]) {
                ArrayList<QueryTerm> tokens = tokenizeDoc(results.get(i).docID, engine);
                for (QueryTerm t : tokens) {
                    if (terms.containsKey(t.term)) {
                        double score = terms.get(t.term);
                        terms.remove(t.term);
                        terms.put(t.term, score + (beta * t.weight));
                    } else {
                        terms.put(t.term, beta * t.weight);
                    }
                }
            }
        }
        queryterm = new ArrayList<QueryTerm>();
        for (Map.Entry<String, Double> e : terms.entrySet()) {
            QueryTerm term = new QueryTerm(e.getKey(), e.getValue());
            queryterm.add(term);
        }
    }

    public ArrayList<QueryTerm> tokenizeDoc(int docID, Engine engine) {
        HashMap<String, Integer> tokens = new HashMap<String, Integer>();
        int numTokens = 0;
        try {
            File dokDir = new File( engine.dirNames.get(0) );
            String[] fs = dokDir.list();
            File f = new File( dokDir, fs[docID] );
            Reader reader = new InputStreamReader( new FileInputStream(f), StandardCharsets.UTF_8 );
            Tokenizer tok = new Tokenizer( reader, true, false, true, engine.indexer.patterns_file );

            while ( tok.hasMoreTokens() ) {
                String token = tok.nextToken();
                numTokens++;
                if (!tokens.containsKey(token)) tokens.put(token, 0);
                int count = tokens.get(token);
                tokens.remove(token);
                tokens.put(token, count + 1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        ArrayList<QueryTerm> terms = new ArrayList<QueryTerm>();
        for (Map.Entry<String, Integer> e : tokens.entrySet()) {
            QueryTerm term = new QueryTerm(e.getKey(), (double)e.getValue());
            terms.add(term);
        }

        return terms;
    }
}


