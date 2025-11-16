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
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private static final int BLOCK_SIZE = 128;

    private FEntry[] fEntry;
    private boolean[] freeBlockList;
    private FNode[] fNode;

    public FileSystemManager(String filename, int totalSize) throws FileNotFoundException {
        this.disk = new RandomAccessFile(filename, "rw");

        fEntry = new FEntry[MAXFILES];
        freeBlockList = new boolean[MAXBLOCKS];
        fNode = new FNode[MAXBLOCKS];

        if (instance != null) {
            throw new IllegalStateException("Already initialized");
        }
        instance = this;

        for (int i = 0; i < MAXFILES; i++) {
            fEntry[i] = new FEntry();
        }
        for (int i = 0; i < MAXBLOCKS; i++) {
            fNode[i] = new FNode(i);
            freeBlockList[i] = true;
        }

        metadataBlocks = (int) Math.ceil((double) (MAXFILES * 15 + MAXBLOCKS * 8) / BLOCK_SIZE);

        for (int i = 0; i < metadataBlocks; i++) {
            freeBlockList[i] = false;
        }
    }

    public void createFile(String fileName) throws Exception {
        rwLock.readLock().lock();
        try {
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("File name cannot be null or empty");
            }
            for (FEntry entry : fEntry) {
                if (entry.isUsed() && entry.getFilename().equals(fileName)) {
                    throw new Exception("File already exists.");
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }

        rwLock.writeLock().lock();
        try {
            boolean created = false;
            for (FEntry entry : fEntry) {
                if (!entry.isUsed()) {
                    entry.setFilename(fileName);
                    entry.setFilesize((short) 0);
                    entry.setFirstBlock((short) -1);
                    writeMetadataToDisk();
                    created = true;
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

    public byte[] readFile(String fileName) throws Exception {
        rwLock.readLock().lock();
        try {
            FEntry target = null;
            for (FEntry e : fEntry) {
                if (e.isUsed() && e.getFilename().equals(fileName)) {
                    target = e;
                    break;
                }
            }

            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            byte[] data = new byte[target.getFilesize()];
            int remaining = target.getFilesize();
            int offset = 0;

            int block = target.getFirstBlock();
            while (block != -1 && remaining > 0) {
                disk.seek((block + metadataBlocks) * BLOCK_SIZE);
                int toRead = Math.min(remaining, BLOCK_SIZE);
                disk.readFully(data, offset, toRead);
                offset += toRead;
                remaining -= toRead;
                block = fNode[block].getNext();
            }

            return data;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void writeFile(String fileName, byte[] data) throws Exception {
        rwLock.readLock().lock();
        FEntry target = null;
        int blocksNeeded = (int) Math.ceil((double) data.length / BLOCK_SIZE);
        int freeBlocksAvailable = 0;
        int oldBlockCount = 0;

        try {
            for (FEntry e : fEntry) {
                if (e.isUsed() && e.getFilename().equals(fileName)) {
                    target = e;
                    break;
                }
            }
            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            for (boolean free : freeBlockList) {
                if (free) freeBlocksAvailable++;
            }

            int oldBlock = target.getFirstBlock();
            while (oldBlock != -1) {
                oldBlockCount++;
                oldBlock = fNode[oldBlock].getNext();
            }

        } finally {
            rwLock.readLock().unlock();
        }

        if (freeBlocksAvailable + oldBlockCount < blocksNeeded) {
            throw new Exception("ERROR: file too large");
        }

        rwLock.writeLock().lock();
        try {
            int currentBlock = target.getFirstBlock();
            while (currentBlock != -1) {
                disk.seek((currentBlock + metadataBlocks) * BLOCK_SIZE);
                byte[] zeroBuffer = new byte[BLOCK_SIZE];
                disk.write(zeroBuffer);

                freeBlockList[currentBlock] = true;
                fNode[currentBlock].setBlockIndex(-Math.abs(currentBlock));

                currentBlock = fNode[currentBlock].getNext();
            }

            target.setFirstBlock((short) -1);
            target.setFilesize((short) data.length);
            int prevBlock = -1;

            int dataOffset = 0;
            int remaining = data.length;

            for (int i = 0; i < MAXBLOCKS && remaining > 0; i++) {
                if (freeBlockList[i]) {
                    freeBlockList[i] = false;
                    fNode[i].setBlockIndex(i);
                    fNode[i].setNext(-1);

                    if (prevBlock != -1) {
                        fNode[prevBlock].setNext(i);
                    } else {
                        target.setFirstBlock((short) i);
                    }

                    prevBlock = i;

                    disk.seek((i + metadataBlocks) * BLOCK_SIZE);
                    int chunkSize = Math.min(remaining, BLOCK_SIZE);
                    disk.write(data, dataOffset, chunkSize);
                    dataOffset += chunkSize;
                    remaining -= chunkSize;
                }
            }

            writeMetadataToDisk();

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void deleteFile(String fileName) throws Exception {
        rwLock.writeLock().lock();
        try {
            FEntry target = null;
            for (FEntry e : fEntry) {
                if (e.isUsed() && e.getFilename().equals(fileName)) {
                    target = e;
                    break;
                }
            }

            if (target == null) {
                throw new Exception("ERROR: file " + fileName + " does not exist");
            }

            int block = target.getFirstBlock();
            while (block != -1) {
                disk.seek((block + metadataBlocks) * BLOCK_SIZE);
                byte[] zeroBuffer = new byte[BLOCK_SIZE];
                disk.write(zeroBuffer);

                freeBlockList[block] = true;
                fNode[block].setBlockIndex(-Math.abs(block));

                int nextBlock = fNode[block].getNext();
                fNode[block].setNext(-1);
                block = nextBlock;
            }

            target.setFilename("");
            target.setFilesize((short) 0);
            target.setFirstBlock((short) -1);
            writeMetadataToDisk();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<String> listFiles() throws Exception {
        rwLock.readLock().lock();
        try {
            List<String> names = new ArrayList<>();
            for (FEntry e : fEntry) {
                if (e.isUsed()) names.add(e.getFilename());
            }
            return names;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void writeMetadataToDisk() throws IOException {
        disk.seek(0);
        for (FEntry entry : fEntry) {
            byte[] nameBytes = new byte[11];
            if (entry.isUsed()) {
                byte[] fnameBytes = entry.getFilename().getBytes();
                System.arraycopy(fnameBytes, 0, nameBytes, 0, fnameBytes.length);
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
