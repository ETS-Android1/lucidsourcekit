package com.bitflaker.lucidsourcekit.general.database.values;

public enum DreamClarity {
    VeryCloudy("VCL"),
    Cloudy("CLD"),
    Clear("CLR"),
    CrystalClear("CCL"),
    None("");

    private String id;

    DreamClarity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static DreamClarity getEnum(String id){
        for (DreamClarity enm : DreamClarity.values()) {
            if (enm.getId().equalsIgnoreCase(id)) {
                return enm;
            }
        }
        return null;
    }
}
