/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.io.Serializable;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {

    public int docID;
    public double score = 0;
    public ArrayList<Integer> offsets = new ArrayList<Integer>();

    public PostingsEntry(int docID, int offset) {
        this.docID = docID;
        insertOffset(offset);
    }

    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */
    public int compareTo( PostingsEntry other ) {
       return Double.compare( other.score, score );
    }

    // Insert offset to list of offsets
    public void insertOffset( int offset ) {
        int index = binarySearch(offset);
        if (index == offsets.size()) offsets.add(offset);
        else offsets.add(index, offset);
    }

    private int binarySearch(int target) {
        if (offsets.size() == 0) return 0;

        int start = 0;
        int end = offsets.size() - 1;
        int mid;
        
        if (target < offsets.get(start)) return start;
        else if (target > offsets.get(end)) return end + 1;
        
        while (start + 1 < end) {
            mid = start + (end - start) / 2;
            if (offsets.get(mid) > target) end = mid;
            else start = mid;
        }
        
        return offsets.get(start) >= target ? start : end;
    }
}

