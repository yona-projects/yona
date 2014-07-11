package models;

/**
 * @author Keeun Baik
 */
public class PageParam {

    // start from 0
    private int page;

    // size of one page
    private int size;

    public PageParam(int page, int size) {
        this.page = page;
        this.size = size;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

}
