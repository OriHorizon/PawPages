package com.orihorizon.pawpages.pdf;

import android.os.ParcelFileDescriptor;
import com.orihorizon.pawpages.pdf.IPdfDocument;

interface IPdfService {
    IPdfDocument open(in ParcelFileDescriptor source);
}
