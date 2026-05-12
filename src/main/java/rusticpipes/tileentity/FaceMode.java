package rusticpipes.tileentity;

public enum FaceMode {
    INPUT,   // pull items from adjacent inventory into network
    OUTPUT;  // push items from network into adjacent inventory

    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
