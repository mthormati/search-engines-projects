/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;

    /** PageRankSparse object to be used for retrieving pagerank */
    PageRankSparse prSparse;

    /** Weightings for TFIDF and pagerank */
    final double TFIDFWEIGHT = 0.4;
    final double PRWEIGHT = 0.6;
    
    /** Constructor */
    public Searcher( Index index, KGramIndex kgIndex ) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    public void setPageRankSpare( PageRankSparse prSparse ) {
        this.prSparse = prSparse;
    }

    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType ) {         
        if (query.queryterm.size() < 1) return null;

        boolean wildcard = false;
        for (int i = 0; i < query.queryterm.size(); i++) 
            if (query.queryterm.get(i).term.contains("*")) 
                wildcard = true;

        if (wildcard) {
            if (queryType == QueryType.INTERSECTION_QUERY)
                return generateWildcardIntersection(query);
            else if (queryType == QueryType.PHRASE_QUERY)
                return generateWildcardPhrase(query);
            else
                return generateWildcardRanked(query, queryType, rankingType);
        } else {
            return handleSearchType(query, queryType, rankingType);
        }
    }

    private PostingsList handleSearchType(Query query, QueryType queryType, RankingType rankingType) {
        switch (queryType) {
            case INTERSECTION_QUERY:
                return intersectionSearch(query, 1, index.getPostings(query.queryterm.get(0).term));
            case PHRASE_QUERY:
                return phraseSearch(query, 1, index.getPostings(query.queryterm.get(0).term));
            case RANKED_QUERY:
                switch(rankingType) {
                    case TF_IDF:
                        return rankedSearchTFIDF(query);
                    case PAGERANK:
                        return rankedSearchPR(query);
                    case COMBINATION:
                        return rankedSearchCombination(query);
                    case HITS:
                        return rankedSearchHits(query);
                }
        }
        return null;
    }

    private PostingsList generateWildcardIntersection(Query query) {
        PostingsList postings = new PostingsList();
        PostingsList[] list = new PostingsList[query.queryterm.size()];

        for (int i = 0; i < query.queryterm.size(); i++) {
            PostingsList wordList = new PostingsList();
            String term = query.queryterm.get(i).term;

            if (!term.contains("*")) {
                wordList.mergeLists(index.getPostings(term));
            } else if (term.startsWith("*")) {
                String term2 = term + "$";
                String kgram = term2.substring(1, 3);
                List<KGramPostingsEntry> entries = kgIndex.getPostings(kgram);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.endsWith(term.substring(1)))
                        wordList.mergeLists(index.getPostings(word));
                        
                }
            } else if (term.endsWith("*")) {
                String term2 = "^" + term;
                String kgram = term2.substring(term2.length() - 3, term2.length() - 1);
                List<KGramPostingsEntry> entries = kgIndex.getPostings(kgram);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.startsWith(term.substring(0, term.length() - 1)))
                        wordList.mergeLists(index.getPostings(word));
                }
            } else {
                String term2 = "^" + term + "$";
                int wcPos = term2.indexOf("*");
                String kgramStart = term2.substring(wcPos - 2, wcPos);
                String kgramEnd = term2.substring(wcPos + 1, wcPos + 3);
                List<KGramPostingsEntry> entries1 = kgIndex.getPostings(kgramStart);
                List<KGramPostingsEntry> entries2 = kgIndex.getPostings(kgramEnd);
                List<KGramPostingsEntry> entries = kgIndex.intersect(entries1, entries2);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    wcPos = term.indexOf("*");
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.startsWith(term.substring(0, wcPos)) &&
                        word.endsWith(term.substring(wcPos + 1, term.length()))) 
                        wordList.mergeLists(index.getPostings(word));
                }
            }
            list[i] = wordList;
        }

        postings = list[0];
        for (int i = 1; i < list.length; i++) {
            postings = postings.intersect(list[i]);
        }

        return postings;
    }

    private PostingsList generateWildcardPhrase(Query query) {
        PostingsList postings = new PostingsList();
        PostingsList[] list = new PostingsList[query.queryterm.size()];

        for (int i = 0; i < query.queryterm.size(); i++) {
            PostingsList wordList = new PostingsList();
            String term = query.queryterm.get(i).term;

            if (!term.contains("*")) {
                wordList.mergeLists(index.getPostings(term));
            } else if (term.startsWith("*")) {
                String term2 = term + "$";
                String kgram = term2.substring(1, 3);
                List<KGramPostingsEntry> entries = kgIndex.getPostings(kgram);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.endsWith(term.substring(1))) 
                        wordList.mergeLists(index.getPostings(word));
                }
            } else if (term.endsWith("*")) {
                String term2 = "^" + term;
                String kgram = term2.substring(term2.length() - 3, term2.length() - 1);
                List<KGramPostingsEntry> entries = kgIndex.getPostings(kgram);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    boolean debug = false;
                    if (word.startsWith(term.substring(0, term.length() - 1))) {
                        wordList.mergeLists(index.getPostings(word));
                    }
                }
            } else {
                String term2 = "^" + term + "$";
                int wcPos = term2.indexOf("*");
                String kgramStart = term2.substring(wcPos - 2, wcPos);
                String kgramEnd = term2.substring(wcPos + 1, wcPos + 3);
                List<KGramPostingsEntry> entries1 = kgIndex.getPostings(kgramStart);
                List<KGramPostingsEntry> entries2 = kgIndex.getPostings(kgramEnd);
                List<KGramPostingsEntry> entries = kgIndex.intersect(entries1, entries2);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    wcPos = term.indexOf("*");
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.startsWith(term.substring(0, wcPos)) &&
                        word.endsWith(term.substring(wcPos + 1, term.length()))) 
                        wordList.mergeLists(index.getPostings(word));
                }
            }
            list[i] = wordList;
        }

        postings = list[0];
        for (int i = 1; i < list.length; i++) {
            PostingsList list2 = list[i];
            PostingsList newList = new PostingsList();
            int j = 0, k = 0;
            while (list2 != null && postings != null && j < postings.size() && k < list2.size()) {
                if (postings.get(j).docID == list2.get(k).docID) {
                    ArrayList<Integer> offsets1 = postings.get(j).offsets;
                    ArrayList<Integer> offsets2 = list2.get(k).offsets;
                    int l = 0, m = 0;

                    while (l < offsets1.size() && m < offsets2.size()) {
                        if (offsets2.get(m) == offsets1.get(l) + 1) {
                            newList.add(postings.get(j).docID, offsets2.get(m));
                            l++; m++;
                        } else {
                            if (offsets1.get(l) < offsets2.get(m)) l++;
                            else m++;
                        }
                    }
                    j++; k++;
                } else {
                    if (postings.get(j).docID < list2.get(k).docID) j++;
                    else k++;
                }
            }
            postings = newList;
        }

        return postings;
    }

    private PostingsList generateWildcardRanked(Query query, QueryType queryType, RankingType rankingType) {
        PostingsList postings = new PostingsList();
        HashSet<String> set = new HashSet<String>();

        for (int i = 0; i < query.queryterm.size(); i++) {
            PostingsList wordList = new PostingsList();
            String term = query.queryterm.get(i).term;

            if (!term.contains("*")) {
                set.add(term);
            } else if (term.startsWith("*")) {
                String term2 = term + "$";
                String kgram = term2.substring(1, 3);
                List<KGramPostingsEntry> entries = kgIndex.getPostings(kgram);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.endsWith(term.substring(1)))
                        set.add(word);     
                }
            } else if (term.endsWith("*")) {
                String term2 = "^" + term;
                String kgram = term2.substring(term2.length() - 3, term2.length() - 1);
                List<KGramPostingsEntry> entries = kgIndex.getPostings(kgram);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.startsWith(term.substring(0, term.length() - 1)))
                        set.add(word);
                }
            } else {
                String term2 = "^" + term + "$";
                int wcPos = term2.indexOf("*");
                String kgramStart = term2.substring(wcPos - 2, wcPos);
                String kgramEnd = term2.substring(wcPos + 1, wcPos + 3);
                List<KGramPostingsEntry> entries1 = kgIndex.getPostings(kgramStart);
                List<KGramPostingsEntry> entries2 = kgIndex.getPostings(kgramEnd);
                List<KGramPostingsEntry> entries = kgIndex.intersect(entries1, entries2);
                String word;
                for (int j = 0; j < entries.size(); j++) {
                    wcPos = term.indexOf("*");
                    word = kgIndex.id2term.get(entries.get(j).tokenID);
                    if (word.startsWith(term.substring(0, wcPos)) &&
                        word.endsWith(term.substring(wcPos + 1, term.length()))) 
                        set.add(word);
                }
            }
        }

        StringBuilder queryStr = new StringBuilder();
        for (String word : set) {
            queryStr.append(word).append(" ");
        }
        postings = handleSearchType(new Query(queryStr.toString()), queryType, rankingType);

        return postings;
    }

    public PostingsList intersectionSearch( Query query, int pos, PostingsList list1 ) {
        if (list1 == null) return null;
        if (pos >= query.queryterm.size()) return list1;

        PostingsList list2 = index.getPostings(query.queryterm.get(pos).term);
        PostingsList newList = new PostingsList();
        int i = 0, j = 0;
        while (list2 != null && i < list1.size() && j < list2.size()) {
            if (list1.get(i).docID == list2.get(j).docID) {
                newList.add(list1.get(i).docID, 0);
                i++; j++;
            } else {
                if (list1.get(i).docID < list2.get(j).docID) i++;
                else j++;
            }
        }

        return intersectionSearch(query, pos + 1, newList);
    }

    private PostingsList phraseSearch( Query query, int pos, PostingsList list1 ) {
        if (list1 == null) return null;
        if (pos >= query.queryterm.size()) return list1;

        PostingsList list2 = index.getPostings(query.queryterm.get(pos).term);
        PostingsList newList = new PostingsList();
        int i = 0, j = 0;
        while (list2 != null && i < list1.size() && j < list2.size()) {
            if (list1.get(i).docID == list2.get(j).docID) {
                ArrayList<Integer> offsets1 = list1.get(i).offsets;
                ArrayList<Integer> offsets2 = list2.get(j).offsets;
                int k = 0, l = 0;

                while (k < offsets1.size() && l < offsets2.size()) {
                    if (offsets2.get(l) == offsets1.get(k) + 1) {
                        newList.add(list1.get(i).docID, offsets2.get(l));
                        k++; l++;
                    } else {
                        if (offsets1.get(k) < offsets2.get(l)) k++;
                        else l++;
                    }
                }
                i++; j++;
            } else {
                if (list1.get(i).docID < list2.get(j).docID) i++;
                else j++;
            }
        }

        return phraseSearch(query, pos + 1, newList);   
    }

    private PostingsList rankedSearchTFIDF( Query query ) {
        final int numDocs = index.docLengths.size();
        double[] scores = new double[numDocs];

        for (int i = 0; i < query.queryterm.size(); i++) {
            String term = query.queryterm.get(i).term;
            PostingsList postings = index.getPostings(term);
                        
            if (postings == null) continue;

            PostingsEntry entry;
            for (int j = 0; j < postings.size(); j++) {
                entry = postings.get(j);
                double tfidfDoc = query.queryterm.get(i).weight * (double)entry.offsets.size() * Math.log((double)(numDocs) / (double)(postings.size()));
                scores[entry.docID] += tfidfDoc;
            }
        }

        PostingsList newList = new PostingsList();
        for (int i = 0; i < numDocs; i++) {
            scores[i] = scores[i] / index.docLengths.get(i);
            if (scores[i] > 0) {
                newList.add(i, 0);
                newList.setScore(i, scores[i]);
            }
        }
        newList.sortList();

        return newList;
    }

    private PostingsList rankedSearchPR( Query query) {
        PostingsList list = rankedSearchTFIDF(query);

        for (int i = 0; i < list.size(); i++) {
            PostingsEntry entry = list.get(i);
            entry.score = prSparse.getScore(index.docNames.get(entry.docID));
        }
        list.sortList();
        
        System.err.println(list.get(0).docID);

        return list;
    }

    private PostingsList rankedSearchCombination( Query query ) {
        PostingsList list = rankedSearchTFIDF(query);

        for (int i = 0; i < list.size(); i++) {
            PostingsEntry entry = list.get(i);
            entry.score = entry.score * TFIDFWEIGHT + prSparse.getScore(index.docNames.get(entry.docID)) * PRWEIGHT;
        }
        list.sortList();

        return list;
    }

    private PostingsList union(Query query) {
        PostingsList list = new PostingsList();
        for (int i = 0; i < query.length(); i++) {
            String term = query.queryterm.get(i).term;
            PostingsList postings = index.getPostings(term);

            for (int j = 0; j < postings.size(); j++) {
                list.add(postings.get(j).docID, 0);
            }
        }
        return list;
    }

    private PostingsList rankedSearchHits( Query query ) {
        PostingsList list = union(query);
        HITSRanker hits = new HITSRanker("linksDavis.txt", "davisTitles.txt", index, list);
        list = hits.rank();
        list.sortList();
        return list;
    }
}