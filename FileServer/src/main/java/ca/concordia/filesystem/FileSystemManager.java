package ca.concordia.filesystem;

import java.io.FileNotFoundException;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;


public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] fEntry; // Array of fNode, changed names for easier tracking through project
    private boolean[] freeBlockList; // Bitmap for free blocks
    private FNode[] fNode;//changed names for easier tracking through project

    public FileSystemManager(String filename, int totalSize) throws FileNotFoundException {//add throws io exeption?
        // Initialize the file system manager with a file
        this.disk = new RandomAccessFile(filename, "rw");

        fEntry = new FEntry[MAXFILES];
        freeBlockList = new boolean[MAXBLOCKS];
        fNode = new FNode[MAXBLOCKS];


        if(instance == null) {
            //TODO Initialize the file system
            for(int i = 0; i < MAXFILES; i++) {
                fEntry[i] = new FEntry();//initialise file entries
            }
            for(int i = 0; i < MAXBLOCKS; i++) {
                fNode[i] = new FNode();
                freeBlockList[i] = true; // All blocks are free initially
            }  

            //reserve blocks for metadata, add metsdata size calculation
            int metadataBlocks = (int) Math.ceil((double)(MAXFILES * 15 + MAXBLOCKS * 4) / BLOCK_SIZE);
            
            for(int i = 0; i < metadataBlocks; i++) {
                freeBlockList[i] = false; // Reserve blocks for metadata
            }


        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName) throws Exception {
        // TODO
        globalLock.lock();
        try {
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("File name cannot be null or empty");
            }
            // Check if file already exists
            for (FEntry entry : fEntry) {
                if (entry.isUsed() && entry.getFilename().equals(fileName)) {
                    throw new Exception("File already exists.");
                }
            }
            // Find a free FEntry
            boolean created = false;
            for (FEntry entry : fEntry) {
                if (!entry.isUsed()) {
                    entry.setFilename(fileName);
                    entry.setFilesize((short)0);
                   // entry.setFirstBlock((short)-1);
                    created = true;
                    System.out.println("Created file: " + fileName);
                    break;
                }
            }

            if (!created) {
                throw new Exception("Maximum file limit reached.");
            }


        } finally {
            globalLock.unlock();
        }



        throw new UnsupportedOperationException("Method not implemented yet.");
    }


    // required Read, Write, Delete, List methods to be implemented

    public void readFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public void writeFile(String fileName, byte[] data) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public void deleteFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public List<String> listFiles() throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

// Helper methods



}
