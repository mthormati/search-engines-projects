/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, KTH, 2018
 */  

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *   
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks. 
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "./index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME = "dictionary";

    /** The dictionary file name */
    public static final String DATA_FNAME = "data";

    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** Pointer to the first free memory cell in the data file. */
    long free = 1L;

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();
    HashMap<String, Long> terms = new HashMap<String, Long>();


    // ===================================================================

    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */ 
    public class Entry {
        long key;
        int size;
        int hash;

        public Entry(long key, int size, int hash) {
            this.key = key;
            this.size = size;
            this.hash = hash;
        }
    }

    // ==================================================================

    
    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created. 
     */
    public PersistentHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dictionaryFile.setLength( TABLESIZE * 16 );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        try {
            readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */ 
    int writeData( BufferedWriter outStream, String dataString, long ptr ) {
        try {
            outStream.write( dataString, 0, dataString.length() );
            return dataString.getBytes().length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     *  Reads data from the data file
     */ 
    String readData( long ptr, int size ) {
        try {
            dataFile.seek( ptr );
            byte[] data = new byte[size];
            dataFile.readFully( data );
            return new String(data);
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================
    //
    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file. 
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     *  @return The number of collisions
     */
    int writeEntry( String token, Entry entry, long ptr ) {
        int collisions = 0;
        try {
            //Collision detection
            dictionaryFile.seek( ptr );
            int hash = dictionaryFile.readInt();
            while (hash != 0) {
                collisions += 1;
                dictionaryFile.skipBytes(12);
                if (dictionaryFile.getFilePointer() == TABLESIZE * 16) {
                    dictionaryFile.seek(0);
                }
                hash = dictionaryFile.readInt();
            }
            dictionaryFile.seek(dictionaryFile.getFilePointer() - 4);
            terms.put(token, dictionaryFile.getFilePointer());
            //Write to dictionary file
            dictionaryFile.writeInt( entry.hash );
            dictionaryFile.writeLong( entry.key );
            dictionaryFile.writeInt( entry.size );
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        return collisions;
    }

    /**
     *  Reads an entry from the dictionary file.
     *
     *  @param ptr The place in the dictionary file where to start reading.
     */
    Entry readEntry( long ptr, String token ) {   
        try {
            dictionaryFile.seek(ptr);
            int hash = dictionaryFile.readInt();
            if (hash != token.hashCode()) return null;
            long dataPtr = dictionaryFile.readLong();
            int size = dictionaryFile.readInt();
            return new Entry(dataPtr, size, hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo" );
        for (Map.Entry<Integer,String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write(docInfoEntry.getBytes());
        }
        fout.close();
    }

    private void writeTerms() {
        try {
            BufferedWriter outstream = new BufferedWriter(new FileWriter( INDEXDIR + "/" + TERMS_FNAME ));
            for (Map.Entry<String, Long> entry : terms.entrySet()) {
                outstream.write(entry.getKey() + ";" + entry.getValue() + "\n");
            }
            outstream.flush();
            outstream.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    private void readDocInfo() throws IOException {
        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try (BufferedReader br = new BufferedReader(freader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                docNames.put(new Integer(data[0]), data[1]);
                docLengths.put(new Integer(data[0]), new Integer(data[2]));
            }
        }
        freader.close();

        try {
            BufferedReader instream = new BufferedReader(new FileReader( INDEXDIR + "/" + TERMS_FNAME ));
            String line = instream.readLine();
            while (line != null) {
                String token = line.substring(0, line.indexOf(";"));
                long ptr = Long.parseLong(line.substring(line.indexOf(";") + 1));
                terms.put(token, ptr);
                line = instream.readLine();
            }
            instream.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *  Write the index to files.
     */
    public void writeIndex() {
        int collisions = 0;
        try {
            // Write the 'docNames' and 'docLengths' hash maps to a file
            writeDocInfo();       

            // Write the dictionary and the postings list
            BufferedWriter dataOutStream = new BufferedWriter(new FileWriter( INDEXDIR + "/" + DATA_FNAME ));
            dataOutStream.write('0');
            for (Map.Entry<String, PostingsList> entry : index.entrySet()) {
                //Construct string for postingslist
                StringBuilder serialization = new StringBuilder();
                serialization.append(entry.getKey())
                             .append(";")
                             .append(entry.getValue().toStringBuilder());
                //Write entry to dictionary
                long ptr = (Math.abs(entry.getKey().hashCode()) % TABLESIZE) * 16;
                Entry newEntry = new Entry(free, serialization.toString().getBytes().length, entry.getKey().hashCode()); 
                collisions += writeEntry(entry.getKey(), newEntry, ptr);
                //Write postings list to data file
                free += writeData(dataOutStream,  serialization.toString(), free);
            }
            dataOutStream.flush();
            dataOutStream.close();

            writeTerms();
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
    }


    // ==================================================================


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        if (!terms.containsKey(token)) return null;

        long dictPtr = terms.get(token);
        Entry entry = readEntry(dictPtr, token);

        // long dictPtr = (Math.abs(token.hashCode()) % TABLESIZE) * 16;
        // long startPtr = dictPtr;

        // while (entry == null) {
        //     dictPtr += 16;
        //     if (dictPtr == TABLESIZE * 16) dictPtr = 0;
        //     entry = readEntry(dictPtr, token);
        //     if (dictPtr == startPtr) return null;
        // }
        
        String data = readData(entry.key, entry.size);
        return new PostingsList(data);
    }


    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        PostingsList list = index.get(token);
        if (list != null) {
            list.add(docID, offset);
            index.put(token, list);
        } else {
            PostingsList newList = new PostingsList();
            newList.add(docID, offset);
            index.put(token, newList);
        }
    }


    /**
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
        System.err.println( index.keySet().size() + " unique words" );
        System.err.print( "Writing index to disk..." );
        writeIndex();
        System.err.println( "done!" );
    }
}
