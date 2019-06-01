/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class PostingsList {
    
    /** The postings list */
    private ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();
    private HashMap<Integer, Integer> idMap = new HashMap<Integer, Integer>();

    public PostingsList() {}

    public PostingsList(String str) {
        String[] docList = str.split("~");
        for (int i = 1; i < docList.length; i++) {
            String[] offsetList = docList[i].split(",");
            try {
                int docID = Integer.parseInt(offsetList[0]);
                for (int j = 1; j < offsetList.length; j++) {
                        add(docID, Integer.parseInt(offsetList[j]));

                }
            } catch (NumberFormatException e) {
                System.err.println("List: " + str);
            }
        }
    }

    /** Number of postings in this list. */
    public int size() {
        return list.size();
    }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) {
        return list.get( i );
    }

    public PostingsEntry getEntry( int docID ) {
        Integer pos = idMap.get(docID);
        if (pos != null) return list.get(idMap.get(docID));
        return null;
    }

    /* Adds an element to the list */
    public void add( int docID, int offset ) {
        PostingsEntry entry = getEntry(docID);
        //If list contains docID
        if (entry != null) {
            entry.insertOffset(offset);
        } else {
            PostingsEntry newEntry = new PostingsEntry(docID, offset);
            int index = binarySearch(docID);
            if (index == list.size()) list.add(newEntry);
            else list.add(index, newEntry);
            idMap.put(docID, index);
        }
    }

    public void setScore( int docID, double score ) {
        PostingsEntry entry = getEntry(docID);
        if (entry != null) {
            entry.score = score;
        } 
    }

    public StringBuilder toStringBuilder() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            PostingsEntry entry = list.get(i);
            str.append("~").append(entry.docID).append(",");
            for (int j = 0; j < entry.offsets.size(); j++) {
                str.append(entry.offsets.get(j)).append(",");
            } 
        }
        return str;
    }

    //Merge p2 into p1
    public void mergeLists(PostingsList p2) {
        for (int i = 0; i < p2.size(); i++) {
            for (int  j = 0; j < p2.get(i).offsets.size(); j++) {
                add(p2.get(i).docID, p2.get(i).offsets.get(j));
            }
        }
    }

    //Sort the list
    public void sortList() {
        Collections.sort(list);
    }

    private int binarySearch(int target) {
        if (list.size() == 0) return 0;

        int start = 0;
        int end = list.size() - 1;
        int mid;
        
        if (target < list.get(start).docID) return start;
        else if (target > list.get(end).docID) return end + 1;
        
        while (start + 1 < end) {
            mid = start + (end - start) / 2;
            if (list.get(mid).docID > target) end = mid;
            else start = mid;
        }
        
        return list.get(start).docID >= target ? start : end;
    }
}

