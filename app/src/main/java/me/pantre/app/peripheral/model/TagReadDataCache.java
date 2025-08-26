package me.pantre.app.peripheral.model;


import java.util.ArrayList;
import java.util.List;

public class TagReadDataCache {

    private final List<TagReadData> tagsReadData = new ArrayList<>();
    private long tagsReadingCycle = 0;

    public void updateReadingCycle(final long readingCycle) {
        if (tagsReadingCycle != readingCycle) {
            tagsReadingCycle = readingCycle;
            tagsReadData.clear();
        }
    }

    public void add(final TagReadData tagReadData) {
        for (int i = 0; i < tagsReadData.size(); i++) {
            TagReadData oldTagData = tagsReadData.get(i);
            if (tagReadData.getEpc().equals(oldTagData.getEpc())) {
                if (tagReadData.getRssi() > oldTagData.getRssi()) {
                    tagsReadData.set(i, tagReadData);
                }
                return;
            }
        }
        tagsReadData.add(tagReadData);
    }

    public List<TagReadData> getTemperatureTags() {
        List<TagReadData> tempTags = new ArrayList<>();
        for (TagReadData tag : tagsReadData) {
            if (tag.isTemperatureTag()) {
                tempTags.add(tag);
            }
        }
        return tempTags;
    }
}
