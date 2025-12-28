package com.ltrudu.sitewatcher.util;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for file compression operations.
 * Provides methods for creating and extracting ZIP archives.
 * Thread-safe static methods for compression/decompression.
 */
public final class CompressionHelper {

    private static final String TAG = "CompressionHelper";
    private static final int BUFFER_SIZE = 8192;

    // Private constructor to prevent instantiation
    private CompressionHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Compress a file or directory to a ZIP archive.
     * @param source The source file or directory to compress
     * @param destination The destination ZIP file
     * @throws IOException If an I/O error occurs
     */
    public static void compressToZip(@NonNull File source, @NonNull File destination)
            throws IOException {
        if (!source.exists()) {
            throw new IOException("Source file does not exist: " + source.getAbsolutePath());
        }

        // Ensure parent directory exists
        File parentDir = destination.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        try (FileOutputStream fos = new FileOutputStream(destination);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {

            if (source.isDirectory()) {
                compressDirectory(source, source.getName(), zos);
            } else {
                compressFile(source, source.getName(), zos);
            }
        }
    }

    /**
     * Recursively compress a directory.
     */
    private static void compressDirectory(@NonNull File directory, @NonNull String basePath,
            @NonNull ZipOutputStream zos) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String entryPath = basePath + "/" + file.getName();
            if (file.isDirectory()) {
                compressDirectory(file, entryPath, zos);
            } else {
                compressFile(file, entryPath, zos);
            }
        }
    }

    /**
     * Compress a single file to the ZIP output stream.
     */
    private static void compressFile(@NonNull File file, @NonNull String entryName,
            @NonNull ZipOutputStream zos) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
        }

        zos.closeEntry();
    }

    /**
     * Decompress a ZIP archive to a destination directory.
     * @param zipFile The ZIP file to decompress
     * @param destinationDir The destination directory
     * @throws IOException If an I/O error occurs
     */
    public static void decompressZip(@NonNull File zipFile, @NonNull File destinationDir)
            throws IOException {
        if (!zipFile.exists()) {
            throw new IOException("ZIP file does not exist: " + zipFile.getAbsolutePath());
        }

        if (!destinationDir.exists()) {
            if (!destinationDir.mkdirs()) {
                throw new IOException("Failed to create directory: " +
                        destinationDir.getAbsolutePath());
            }
        }

        String canonicalDestPath = destinationDir.getCanonicalPath();

        try (FileInputStream fis = new FileInputStream(zipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outputFile = new File(destinationDir, entry.getName());

                // Security check: prevent zip slip vulnerability
                String canonicalOutputPath = outputFile.getCanonicalPath();
                if (!canonicalOutputPath.startsWith(canonicalDestPath)) {
                    throw new IOException("ZIP entry is outside of target directory: " +
                            entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " +
                                outputFile.getAbsolutePath());
                    }
                } else {
                    // Ensure parent directory exists
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        if (!parentDir.mkdirs()) {
                            throw new IOException("Failed to create directory: " +
                                    parentDir.getAbsolutePath());
                        }
                    }

                    extractFile(zis, outputFile);

                    // Preserve modification time
                    if (entry.getTime() > 0) {
                        outputFile.setLastModified(entry.getTime());
                    }
                }

                zis.closeEntry();
            }
        }
    }

    /**
     * Extract a file from the ZIP input stream.
     */
    private static void extractFile(@NonNull ZipInputStream zis, @NonNull File outputFile)
            throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = zis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Check if a file is a valid ZIP archive.
     * @param file The file to check
     * @return true if the file is a valid ZIP archive
     */
    public static boolean isValidZip(@NonNull File file) {
        if (!file.exists() || !file.isFile()) {
            return false;
        }

        // ZIP files start with PK (0x504B)
        try (FileInputStream fis = new FileInputStream(file)) {
            int byte1 = fis.read();
            int byte2 = fis.read();
            return byte1 == 0x50 && byte2 == 0x4B;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get the uncompressed size of a ZIP archive.
     * @param zipFile The ZIP file
     * @return Total uncompressed size in bytes, or -1 if error
     */
    public static long getUncompressedSize(@NonNull File zipFile) {
        if (!zipFile.exists()) {
            return -1;
        }

        long totalSize = 0;
        try (FileInputStream fis = new FileInputStream(zipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    long entrySize = entry.getSize();
                    if (entrySize > 0) {
                        totalSize += entrySize;
                    }
                }
                zis.closeEntry();
            }
            return totalSize;
        } catch (IOException e) {
            return -1;
        }
    }
}
