package ca.concordia.filesystem.datastructures;

public class FNode {

    private int blockIndex;// negativ efree, positive used
    private int next;//-1 if none

    public FNode(int id) {
        this.blockIndex = -Math.abs(id);
        this.next = -1;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public int getNext() {
        return next;
    }

    public void setNext(int next) {
        this.next = next;
    }

    public boolean isUsed() {
        return blockIndex >= 0;
    }
}
