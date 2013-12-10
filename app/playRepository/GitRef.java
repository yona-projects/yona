package playRepository;

public class GitRef extends VCSRef {
    public GitRef(String name) {
        super(name);
    }

    @Override
    public String name() {
        if (name == null) {
            return null;
        }
        if (name.startsWith("refs") && name.split("/").length >= 3) {
            return name.split("/", 3)[2];
        }
        return name;
    }
}
