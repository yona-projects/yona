package playRepository;

public class VCSRef {
    protected String name;
    
    public VCSRef(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String canonicalName() {
        return name;
    }
    
    @Override
    public String toString() {
        return name();
    }
}
