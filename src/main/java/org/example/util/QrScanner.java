package org.example.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.MultiFormatReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Optional;

public class QrScanner {

    public static Optional<String> decodeFromFile(File f) {
        try {
            BufferedImage image = ImageIO.read(f);
            if (image == null) return Optional.empty();
            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap);
            return Optional.ofNullable(result.getText());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

