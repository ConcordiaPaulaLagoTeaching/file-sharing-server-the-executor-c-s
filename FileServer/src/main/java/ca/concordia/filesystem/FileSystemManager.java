package ca.concordia.filesystem;

import java.io.FileNotFoundException;
import java.io.IOException;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final int metadataBlocks;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();//allow multiple reads but exclusive write

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

        if (instance == null) {
            //TODO Initialize the file system
            for (int i = 0; i < MAXFILES; i++) {
                fEntry[i] = new FEntry();//initialise file entries
            }
            for (int i = 0; i < MAXBLOCKS; i++) {
                fNode[i] = new FNode(i);//initialise fNodes as empty (negative index)
                freeBlockList[i] = true; // All blocks are free initially
            }

            //reserve blocks for metadata, add metsdata size calculation
            metadataBlocks = (int) Math.ceil((double) (MAXFILES * 15 + MAXBLOCKS * 4) / BLOCK_SIZE);

            for (int i = 0; i < metadataBlocks; i++) {
                freeBlockList[i] = false; // Reserve blocks for metadata
            }

        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName) throws Exception {
        rwLock.writeLock().lock();
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
                    entry.setFilesize((short) 0);
                    entry.setFirstBlock((short) -1);
                    writeMetadataToDisk();
                    created = true;
                    System.out.println("Created file: " + fileName);
                    break;
                }
            }

            if (!created) {
                throw new Exception("Maximum file limit reached.");
            }

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // required Read, Write, Delete, List methods to be implemented
    public byte[] readFile(String fileName) throws Exception {//needs to return so cant be void
        rwLock.readLock().lock();
        try {
            FEntry target = null;
            for (FEntry e : fEntry) {
                if (e.isUsed() && e.getFilename().equals(fileName)) {//check file exists
                    target = e;
                    break;
                }
            }
            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            byte[] data = new byte[target.getFilesize()];
            int offset = 0;
            int currentBlock = target.getFirstBlock();
            while (currentBlock != -1 && offset < data.length) {//cycles through linked data blocks
                disk.seek((currentBlock + metadataBlocks) * BLOCK_SIZE);//jump to correct memory location
                int chunk = Math.min(BLOCK_SIZE, data.length - offset);
                disk.readFully(data, offset, chunk);
                offset += chunk;
                currentBlock = fNode[currentBlock].getNext();
            }
            return data;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void writeFile(String fileName, byte[] data) throws Exception {
        rwLock.writeLock().lock();
        try {

            FEntry target = null;
            for (FEntry e : fEntry) {//check file exists
                if (e.isUsed() && e.getFilename().equals(fileName)) {
                    target = e;
                    break;
                }
            }
            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            int blocksNeeded = (int) Math.ceil((double) data.length / BLOCK_SIZE);
            int freeBlocksAvailable = 0;
            for (boolean free : freeBlockList) {//count free blocks
                if (free) {
                    freeBlocksAvailable++;
                }
            }

            int oldBlockCount = 0;
            int oldBlock = target.getFirstBlock();
            while (oldBlock != -1) {
                oldBlockCount++;
                oldBlock = fNode[oldBlock].getNext();//counts bocks that will be overwritten
            }

            if (blocksNeeded > freeBlocksAvailable + oldBlockCount) {//check if enough space in memory to write
                throw new Exception("ERROR: not enough free space to write file");
            }

            //remove old data to be overwritten
            oldBlock = target.getFirstBlock();
            while (oldBlock != -1) {//cycles through all linked data blocks
                freeBlockList[oldBlock] = true;
                int nextBlock = fNode[oldBlock].getNext();
                fNode[oldBlock].setBlockIndex(-Math.abs(oldBlock));
                fNode[oldBlock].setNext(-1);//unlinks next block
                oldBlock = nextBlock;
            }
            target.setFirstBlock((short) -1);
            target.setFilesize((short) 0);

            int prevBlock = -1;//no previous block
            int offset = 0;//no initial offset
            for (int i = 0; i < MAXBLOCKS && blocksNeeded > 0; i++) {
                if (freeBlockList[i]) {//checks if block is free
                    freeBlockList[i] = false;
                    fNode[i].setBlockIndex(i);
                    fNode[i].setNext(-1);

                    if (prevBlock != -1) {//if not first block
                        fNode[prevBlock].setNext(i);//link previous block to current
                    } else {
                        target.setFirstBlock((short) i); //set first block of this file
                    }

                    prevBlock = i;

                    // Write data chunk to disk
                    disk.seek((i + metadataBlocks) * BLOCK_SIZE);//location of first block start point
                    int chunkSize = Math.min(BLOCK_SIZE, data.length - offset);//amount of data to write, a full block or remainder of file
                    disk.write(data, offset, chunkSize);//write chunk of data starting after offset
                    offset += chunkSize;//increase offset by last chunk size
                    blocksNeeded--;//1 less block needed
                }
            }

            target.setFilesize((short) data.length);//update filesize
            writeMetadataToDisk();//update metadata on disk
        } finally {
            rwLock.writeLock().unlock();
        }

    }

    public void deleteFile(String fileName) throws Exception {
        rwLock.writeLock().lock();
        try {
            FEntry target = null;
            for (FEntry e : fEntry) {
                if (e.isUsed() && e.getFilename().equals(fileName)) {//check file exists
                    target = e;
                    break;
                }
            }
            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            int current = target.getFirstBlock();
            byte[] zeros = new byte[BLOCK_SIZE];
            while (current != -1) {
                disk.seek((current + metadataBlocks) * BLOCK_SIZE);
                disk.write(zeros);//delete data by overwriting with zeros
                freeBlockList[current] = true;//mark block as free
                fNode[current].setBlockIndex(-Math.abs(current));//mark fNode as free
                int next = fNode[current].getNext();
                fNode[current].setNext(-1);
                current = next;
            }

            target.setFilename("");//remove metadata
            target.setFilesize((short) 0);
            target.setFirstBlock((short) -1);
            writeMetadataToDisk();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<String> listFiles() throws Exception {
        List<String> names = new ArrayList<>();//array list to hold file names
        for (FEntry e : fEntry) {
            if (e.isUsed()) {
                names.add(e.getFilename());//add filename to list
            }
        }
        return names;
    }

// Helper methods
    private void writeMetadataToDisk() throws IOException {//use FNode??
        disk.seek(0);
        for (FEntry entry : fEntry) {
            byte[] nameBytes = new byte[11];
            if (entry.isUsed()) {
                byte[] fnameBytes = entry.getFilename().getBytes();
                System.arraycopy(fnameBytes, 0, nameBytes, 0, fnameBytes.length);//copy filename into byte array:(SourceArray, position, destArr, pos, length)
            }
            disk.write(nameBytes);
            disk.writeShort(entry.getFilesize());
            disk.writeShort(entry.getFirstBlock());
        }
        for (FNode node : fNode) {
            disk.writeInt(node.getBlockIndex());
            disk.writeInt(node.getNext());
        }

    }

}
