package com.orihorizon.pawpages.pdf;

import android.os.Bundle;

interface IPdfDocument {
    int getPageCount();
    int[] getPageSize(int index);
    void render(int index, int width, int height, in Bundle target);
    void close();
}
