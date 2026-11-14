package ru.moscow.hotspot_tracker.testdata;

import java.util.List;

/** Produces the data rows of one dataset. */
interface RowSource {
    /**
     * Returns a new row.
     *
     * @param index index of the data row from 0
     * @return a modifiable list with one value per column of the first header row
     */
    List<String> next(long index);
}
