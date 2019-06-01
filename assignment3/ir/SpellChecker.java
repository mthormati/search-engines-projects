/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Collections;


public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    Searcher searcher;

    /** The auxiliary class for containing the value of your ranking function for a token */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

        public int compareTo(Object other) {
            if (this.score == ((KGramStat)other).score) return 0;
            return this.score > ((KGramStat)other).score ? -1 : 1;
        }

        public String toString() {
            // return token + ";" + score;
            return token;
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling
     * correction should pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.4;


    /**
      * The threshold for edit distance for a candidate spelling
      * correction to be accepted.
      */
    private static final int MAX_EDIT_DISTANCE = 2;


    public SpellChecker(Index index, KGramIndex kgIndex, Searcher searcher) {
        this.index = index;
        this.kgIndex = kgIndex;
        this.searcher = searcher;
    }

    /**
     *  Computes the Jaccard coefficient for two sets A and B, where the size of set A is 
     *  <code>szA</code>, the size of set B is <code>szB</code> and the intersection 
     *  of the two sets contains <code>intersection</code> elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        return (double) intersection / (szA + szB - intersection);
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming.
     * Allowed operations are:
     *      => insert (cost 1)
     *      => delete (cost 1)
     *      => substitute (cost 2)
     */
    private int editDistance(String s1, String s2) {
        int[][] matrix = new int[s1.length() + 1][s2.length() + 1];
        //Initialize top row
        for (int i = 0; i < s2.length() + 1; i++) {
            matrix[0][i] = i;
        }
        //Initialize left column
        for (int i = 0; i < s1.length() + 1; i++) {
            matrix[i][0] = i;
        }
        //Fill matrix
        for (int i = 1; i < s1.length() + 1; i++) {
            for (int j = 1; j < s2.length() + 1; j++) {
                //Find minimum
                int min = matrix[i][j - 1];
                if (matrix[i - 1][j - 1] < min) min = matrix[i - 1][j - 1];
                if (matrix[i - 1][j] < min) min = matrix[i - 1][j];
                //Check if letters are the same
                if (s1.charAt(i - 1) == s2.charAt(j - 1) && min == matrix[i - 1][j - 1]) {
                    matrix[i][j] = min;
                } else {
                    if (min == matrix[i - 1][j - 1]) 
                        matrix[i][j] = min + 2;
                    else
                        matrix[i][j] = min + 1;
                }
            }
        }

        return matrix[s1.length()][s2.length()];
    }


    private ArrayList<KGramStat> checkHelper(String par) {
        HashSet<String> kgrams = new HashSet<String>();

        //Contruct kgrams of query term
        String term = new StringBuilder().append("^").append(par).append("$").toString();
        for (int i = 0; i < term.length(); i++) {
            StringBuilder gram = new StringBuilder();
            for (int j = i; j < term.length(); j++) {
                gram.append(term.charAt(j));
                if (gram.length() >= kgIndex.K) break;       
            }
            if (gram.length() < kgIndex.K) break;
            kgrams.add(gram.toString());
        }

        //Find words containing kgrams
        HashSet<String> wordSet = new HashSet<String>();
        for (String gram : kgrams) {
            for (KGramPostingsEntry entry : kgIndex.getPostings(gram)) {
                wordSet.add(kgIndex.id2term.get(entry.tokenID));
            }
        }

        //Calculate Jaccard Coefficients
        HashMap<String, Double> newWords = new HashMap<String, Double>();
        int intersection = 0;
        int szB = 0;
        StringBuilder gram;
        Iterator<String> iteration = wordSet.iterator();
        String word;
        int size = wordSet.size();
        for (int j = 0; j < size; j++) {
            word = iteration.next();
            intersection = 0; szB = 0;
            term = new StringBuilder().append("^").append(word).append("$").toString();

            gram = new StringBuilder();
            for (int i = 0; i < term.length(); i++) { 
                if (gram.length() == 0) {
                    gram.append(term.substring(0, kgIndex.K));
                    i += (kgIndex.K - 1);
                } else {
                    StringBuilder newGram = new StringBuilder();
                    newGram.append(gram.substring(kgIndex.K - (kgIndex.K - 1))).append(term.charAt(i));
                    gram = newGram;
                }
                
                if (kgrams.contains(gram.toString())) intersection++;
                szB++;
            }
            double value = jaccard(kgrams.size(), szB, intersection);
            if (value >= JACCARD_THRESHOLD)
                newWords.put(word, value);
        }

        //Calculate edit distance
        ArrayList<KGramStat> finalWords = new ArrayList<KGramStat>();
        PostingsList tmp;
        int distance;
        for (Map.Entry<String, Double> entry : newWords.entrySet()) {
            distance = editDistance(entry.getKey(), par);
            if (distance <= MAX_EDIT_DISTANCE) {
                word = entry.getKey();
                tmp = index.getPostings(word);
                if (tmp == null) 
                    finalWords.add(new KGramStat(entry.getKey(), 1 / entry.getValue() / distance));
                else
                    finalWords.add(new KGramStat(entry.getKey(), tmp.size() / entry.getValue() / distance));
            }
        }

        Collections.sort(finalWords);
        return finalWords;
    }

    /**
     *  Checks spelling of all terms in <code>query</code> and returns up to
     *  <code>limit</code> ranked suggestions for spelling correction.
     */
    public String[] check(Query query, int limit) {
        int size = query.queryterm.size();
        if (size == 0) return null;

        ArrayList<ArrayList<KGramStat>> terms = new ArrayList<ArrayList<KGramStat>>();
        String term;
        ArrayList<KGramStat> list; 
        for (int i = 0; i < size; i++) {
            list = new ArrayList<KGramStat>();
            term = query.queryterm.get(i).term;
            if (index.getPostings(term) == null)
                list = checkHelper(term);
            else
                list.add(new KGramStat(term, 1));
            terms.add(list);
        }

        // for (int i = 0; i < terms.size(); i++) {
        //     System.err.println(terms.get(i));
        // }
        // System.err.println();

        ArrayList<KGramStat> finalWords = mergeCorrections(terms, limit);
        if (finalWords.size() < limit) 
            size = finalWords.size();
        else
            size = limit;
        
        String[] result = new String[size];
        for (int i = 0; i < size; i++) {
            result[i] = finalWords.get(i).toString();
        }

        return result;
    }

    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  <code>qCorrections</code> into one final merging of query phrases. Returns up
     *  to <code>limit</code> corrected phrases.
     */
    private ArrayList<KGramStat> mergeCorrections(ArrayList<ArrayList<KGramStat>> qCorrections, int limit) {
        if (qCorrections.size() == 1) return qCorrections.get(0);

        ArrayList<KGramStat> phrases = new ArrayList<KGramStat>();
        for (int i = 0; i < (qCorrections.get(0).size() / 2 + 1) && i < (limit / 2 + 1); i++) {
            phrases.add(new KGramStat(qCorrections.get(0).get(i).token, qCorrections.get(0).get(i).score));
        }
        PostingsList list = new PostingsList();
        String phrase = new String();
        Query query;
        for (int i = 1 ; i < qCorrections.size() && i < limit; i++) {
            ArrayList<KGramStat> phrases2 = qCorrections.get(i);
            ArrayList<KGramStat> phraseTmp = new ArrayList<KGramStat>();

            for (int j = 0; j < phrases.size(); j++) {
                for (int k = 0; k < (phrases2.size() / 2 + 1) && i < (limit / 2 + 1); k++) {
                    phrase = new StringBuilder(phrases.get(j).token).append(" ").append(phrases2.get(k).token).toString();
                    query = new Query(phrase);
                    list = searcher.intersectionSearch(query, 1, index.getPostings(query.queryterm.get(0).term));
                    if (list != null) {
                        if (i >= qCorrections.size() - 1 || i >= limit - 1) 
                            phraseTmp.add(new KGramStat(phrase, (phrases.get(j).score + phrases2.get(k).score) * list.size()));
                        else
                            phraseTmp.add(new KGramStat(phrase, phrases.get(j).score + phrases2.get(k).score));
                    }
                }
            }

            phrases = phraseTmp;
        }

        Collections.sort(phrases);
        return phrases;
    }
}
