package ir;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.Semaphore;
import ir.PostingsList;
import java.nio.charset.*;

public class PersistentScalableHashedIndex implements Index, Runnable {

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
    // public static final long TABLESIZE = 611953L;
    public static final long TABLESIZE = 3500000L;

    /** Each intermediate index can fit this many entries */
    // public static final long MAXINDEX = 1500000L;
    public static final long MAXINDEX = 7000000L;

    private static final Semaphore sem = new Semaphore(1, true);

    long tokensProcessed = 0L;

    /** Pointer to the first free memory cell in the data file. */
    long free = 1L;

    /** The version of the current dictionary and data files */
    int version = 1;

    boolean finalRun = false;

    /** Used to determine if file is ready to merge */
    boolean mergeReady = true;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();
    Set<String> terms = new HashSet<String>();

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
    public PersistentScalableHashedIndex() {
        try {
            readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    public PersistentScalableHashedIndex(int version, Set<String> terms) {
        this.version = version;
        this.terms = terms.stream().map(String::new).collect(Collectors.toSet());
        terms.clear();
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */ 
    int writeData( BufferedWriter outStream, String dataString, long ptr ) {
        try {
            outStream.write(dataString);
            return dataString.getBytes().length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     *  Reads data from the data file
     */ 
    String readData( RandomAccessFile file, long ptr, int size ) {
        try {
            file.seek( ptr );
            byte[] data = new byte[size];
            file.readFully( data );
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
    int writeEntry( RandomAccessFile file, Entry entry, long ptr ) {
        int collisions = 0;
        try {
            //Collision detection
            file.seek( ptr );
            int hash = file.readInt();
            while (hash != 0) {
                collisions += 1;
                file.skipBytes(12);
                if (file.getFilePointer() == TABLESIZE * 16) {
                    file.seek(0);
                }
                hash = file.readInt();
            }
            file.seek(file.getFilePointer() - 4);
            //Write to dictionary file
            file.writeInt( entry.hash );
            file.writeLong( entry.key );
            file.writeInt( entry.size );
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
    Entry readEntry( RandomAccessFile file, long ptr, String token ) {   
        try {
            file.seek(ptr);
            int hash = file.readInt();
            if (hash != token.hashCode()) return null;
            long dataPtr = file.readLong();
            int size = file.readInt();
            return new Entry(dataPtr, size, hash);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo", true );
        for (Map.Entry<Integer,String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write(docInfoEntry.getBytes());
        }
        fout.close();
        docNames.clear();
        docLengths.clear();
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
    }

    public void run() {
        try {
            sem.acquire();
            if (version > 1) {
                version++;
                try {
                    //Writers for new merge file
                    BufferedWriter outstream;
                    if (finalRun) {
                        dictionaryFile = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME, "rw");    
                        outstream = new BufferedWriter(new FileWriter(INDEXDIR + "/" + DATA_FNAME));
                    } else {
                        dictionaryFile = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + version, "rw");    
                        outstream = new BufferedWriter(new FileWriter(INDEXDIR + "/" + DATA_FNAME + version));
                    }
                    dictionaryFile.setLength( TABLESIZE * 16 );
                    outstream.write('0');
                    //File access to old files
                    RandomAccessFile dictionary1 = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + (version - 2), "r");
                    RandomAccessFile dictionary2 = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME + (version - 1), "r");
                    RandomAccessFile data1 = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + (version - 2), "r");
                    RandomAccessFile data2 = new RandomAccessFile(INDEXDIR + "/" + DATA_FNAME + (version - 1), "r");

                    while (dictionary1.getFilePointer() < dictionary1.length()) {
                        int hashcode = dictionary1.readInt();
                        long key = dictionary1.readLong();
                        int size = dictionary1.readInt();

                        if (key == 0) continue;

                        Entry entry = new Entry(key, size, hashcode);
                        //Find data string linked with entry
                        String data = readData(data1, entry.key, entry.size);
                        String term = data.substring(0, data.indexOf(";"));

                        //Check if word is also in data2
                        if (terms.contains(term)) {
                            PostingsList list = new PostingsList(data);
                            //Get postingslist from data2 ==================================================
                            long ptr = (Math.abs(term.hashCode()) % TABLESIZE) * 16;
                            Entry entry2 = readEntry(dictionary2, ptr, term);
                            while (entry2 == null) {
                                ptr += 16;
                                if (ptr == TABLESIZE * 16) ptr = 0;
                                entry2 = readEntry(dictionary2, ptr, term);
                            }
                            PostingsList list2 = new PostingsList(readData(data2, entry2.key, entry2.size));
                            //Merge list and list2
                            list.mergeLists(list2);
                            StringBuilder temp = new StringBuilder();
                            temp.append(term).append(";").append(list.toStringBuilder());
                            data = temp.toString();
                            terms.remove(term);
                        } 
                        long ptr = (Math.abs(term.hashCode()) % TABLESIZE) * 16;
                        entry.key = free;
                        entry.size = data.getBytes().length;
                        writeEntry(dictionaryFile, entry, ptr);
                        free += writeData(outstream, data, free);
                    }

                    for (String s : terms) {
                        long dictPtr = (Math.abs(s.hashCode()) % TABLESIZE) * 16;
                        Entry entry = readEntry(dictionary2, dictPtr, s);
                        while (entry == null) {
                            dictPtr += 16;
                            if (dictPtr == TABLESIZE * 16) dictPtr = 0;
                            entry = readEntry(dictionary2, dictPtr, s);
                        }
                        String data = readData(data2, entry.key, entry.size);
                        entry.key = free;
                        dictPtr = (Math.abs(s.hashCode()) % TABLESIZE) * 16;
                        writeEntry(dictionaryFile, entry, dictPtr);
                        free += writeData(outstream, data, free);
                    }

                    outstream.flush();
                    outstream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.err.println("Thread done");
            sem.release();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Write the index to files.
     */
    public void writeIndex() {
        int collisions = 0;
        // version++;

        try {
            // Write the 'docNames' and 'docLengths' hash maps to a file
            writeDocInfo();

            //Create files to write index
            RandomAccessFile tempDictionary = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + version, "rw" );
            RandomAccessFile tempData = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + version, "rw" );
            tempDictionary.setLength( TABLESIZE * 16 );
            terms = index.keySet().stream().map(String::new).collect(Collectors.toSet());

            // Write the dictionary and the postings list
            BufferedWriter dataOutStream = new BufferedWriter(new FileWriter( INDEXDIR + "/" + DATA_FNAME + version ));
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
                collisions += writeEntry(tempDictionary, newEntry, ptr);
                //Write postings list to data file
                free += writeData(dataOutStream,  serialization.toString(), free);
            }
            dataOutStream.flush();
            dataOutStream.close();
            free = 1L;
        } catch(IOException e) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
    }

    /** Inserts a token into the index. */
    public void insert( String token, int docID, int offset ) {
        if (tokensProcessed < MAXINDEX) {
            PostingsList list = index.get(token);
            if (list != null) {
                list.add(docID, offset);
                index.put(token, list);
            } else {
                PostingsList newList = new PostingsList();
                newList.add(docID, offset);
                index.put(token, newList);
            }
            tokensProcessed++;
        } else {
            writeIndex();
            index.clear();
            tokensProcessed = 0;

            insert(token, docID, offset);
            (new Thread(new PersistentScalableHashedIndex(version, terms))).start();
            if (version > 1) version += 2;
            else version++;
        }
    }

    /** Returns the postings for a given term. */
    public PostingsList getPostings( String token ) {
        long dictPtr = (Math.abs(token.hashCode()) % TABLESIZE) * 16;
        long startPtr = dictPtr;

        try {
            dictionaryFile = new RandomAccessFile(INDEXDIR + "/" + DICTIONARY_FNAME, "rw");
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        } catch(FileNotFoundException e) {

        }
        Entry entry = readEntry(dictionaryFile, dictPtr, token);

        while (entry == null) {
            dictPtr += 16;
            if (dictPtr == TABLESIZE * 16) dictPtr = 0;
            entry = readEntry(dictionaryFile, dictPtr, token);
            if (dictPtr == startPtr) return null;
        }

        String data = readData(dataFile, entry.key, entry.size);
        return new PostingsList(data);
    }

    /** This method is called on exit. */
    public void cleanup() {
        // System.err.println( index.keySet().size() + " unique words" );
        System.err.println( "Writing final index..." );
        writeIndex();
        finalRun = true;
        run();
        System.err.println( "done!" );
    }
}