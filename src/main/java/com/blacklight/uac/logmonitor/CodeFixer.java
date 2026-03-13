package com.blacklight.uac.logmonitor;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * CodeFixer - Automatically fixes code issues detected in logs
 * Supports common fix patterns for Java exceptions
 */
public class CodeFixer {
    
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";
    
    /**
     * Fix result containing the patched code and metadata
     */
    public static class FixResult {
        private final boolean success;
        private final String originalCode;
        private final String fixedCode;
        private final String fixDescription;
        private final String filePath;
        private final int lineNumber;
        private final String patchDiff;
        
        public FixResult(boolean success, String originalCode, String fixedCode,
                        String fixDescription, String filePath, int lineNumber, String patchDiff) {
            this.success = success;
            this.originalCode = originalCode;
            this.fixedCode = fixedCode;
            this.fixDescription = fixDescription;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.patchDiff = patchDiff;
        }
        
        public boolean isSuccess() { return success; }
        public String getOriginalCode() { return originalCode; }
        public String getFixedCode() { return fixedCode; }
        public String getFixDescription() { return fixDescription; }
        public String getFilePath() { return filePath; }
        public int getLineNumber() { return lineNumber; }
        public String getPatchDiff() { return patchDiff; }
    }
    
    /**
     * Generate a fix for the detected anomaly
     */
    public FixResult generateFix(LogAnomaly anomaly, String sourceCode) {
        if (!anomaly.isFixable()) {
            return new FixResult(false, sourceCode, sourceCode,
                "Anomaly type not automatically fixable", anomaly.getSourceFile(),
                anomaly.getLineNumber(), "");
        }
        
        return switch (anomaly.getFixStrategy()) {
            case "ADD_NULL_CHECK" -> fixNullPointer(anomaly, sourceCode);
            case "ADD_BOUNDS_CHECK" -> fixArrayBounds(anomaly, sourceCode);
            case "ADD_TYPE_CHECK" -> fixClassCast(anomaly, sourceCode);
            case "ADD_VALIDATION" -> fixInvalidArgument(anomaly, sourceCode);
            default -> new FixResult(false, sourceCode, sourceCode,
                "No fix strategy available", anomaly.getSourceFile(),
                anomaly.getLineNumber(), "");
        };
    }
    
    /**
     * Fix NullPointerException by adding null checks
     */
    private FixResult fixNullPointer(LogAnomaly anomaly, String sourceCode) {
        String[] lines = sourceCode.split("\n");
        int targetLine = anomaly.getLineNumber() - 1; // 0-based index
        
        // If line number is not available, find the vulnerable line
        if (targetLine < 0 || targetLine >= lines.length) {
            targetLine = findVulnerableLine(lines, "NULL_POINTER");
        }
        
        if (targetLine < 0 || targetLine >= lines.length) {
            return new FixResult(false, sourceCode, sourceCode,
                "Could not locate vulnerable line", anomaly.getSourceFile(),
                anomaly.getLineNumber(), "");
        }
        
        String originalLine = lines[targetLine];
        String fixedLine = addNullCheck(originalLine, anomaly.getClassName());
        
        if (fixedLine.equals(originalLine)) {
            return new FixResult(false, sourceCode, sourceCode,
                "Could not determine how to add null check", anomaly.getSourceFile(),
                anomaly.getLineNumber(), "");
        }
        
        // Build fixed code
        StringBuilder fixedCode = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == targetLine) {
                fixedCode.append(fixedLine).append("\n");
            } else {
                fixedCode.append(lines[i]).append("\n");
            }
        }
        
        // Generate patch diff
        String patchDiff = generateDiff(originalLine, fixedLine, targetLine + 1);
        
        return new FixResult(true, sourceCode, fixedCode.toString(),
            "Added null check to prevent NullPointerException",
            anomaly.getSourceFile(), targetLine + 1, patchDiff);
    }
    
    /**
     * Add null check to a line of code
     */
    private String addNullCheck(String line, String className) {
        // Pattern: variable.method() or variable.field
        Pattern pattern = Pattern.compile("(\\w+)\\.(\\w+)");
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            String variable = matcher.group(1);
            String indent = getIndentation(line);
            
            // Create null check wrapper
            return indent + "if (" + variable + " != null) {\n" +
                   indent + "    " + line.trim() + "\n" +
                   indent + "} else {\n" +
                   indent + "    // TODO: Handle null case for " + variable + "\n" +
                   indent + "    logger.warn(\"" + variable + " is null, skipping operation\");\n" +
                   indent + "}";
        }
        
        return line;
    }
    
    /**
     * Fix ArrayIndexOutOfBoundsException by adding bounds checks
     */
    private FixResult fixArrayBounds(LogAnomaly anomaly, String sourceCode) {
        String[] lines = sourceCode.split("\n");
        int targetLine = anomaly.getLineNumber() - 1;
        
        if (targetLine < 0 || targetLine >= lines.length) {
            targetLine = findVulnerableLine(lines, "ARRAY_BOUNDS");
        }
        
        if (targetLine < 0 || targetLine >= lines.length) {
            return new FixResult(false, sourceCode, sourceCode,
                "Could not locate vulnerable line", anomaly.getSourceFile(),
                anomaly.getLineNumber(), "");
        }
        
        String originalLine = lines[targetLine];
        String fixedLine = addBoundsCheck(originalLine);
        
        StringBuilder fixedCode = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == targetLine) {
                fixedCode.append(fixedLine).append("\n");
            } else {
                fixedCode.append(lines[i]).append("\n");
            }
        }
        
        String patchDiff = generateDiff(originalLine, fixedLine, targetLine + 1);
        
        return new FixResult(true, sourceCode, fixedCode.toString(),
            "Added bounds check to prevent ArrayIndexOutOfBoundsException",
            anomaly.getSourceFile(), targetLine + 1, patchDiff);
    }
    
    /**
     * Add bounds check to array access
     */
    private String addBoundsCheck(String line) {
        // Pattern: array[index]
        Pattern pattern = Pattern.compile("(\\w+)\\[(\\w+)\\]");
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            String array = matcher.group(1);
            String index = matcher.group(2);
            String indent = getIndentation(line);
            
            return indent + "if (" + index + " >= 0 && " + index + " < " + array + ".length) {\n" +
                   indent + "    " + line.trim() + "\n" +
                   indent + "} else {\n" +
                   indent + "    // TODO: Handle out of bounds access\n" +
                   indent + "    logger.warn(\"Index \" + " + index + " + \" out of bounds for array of length \" + " + array + ".length);\n" +
                   indent + "}";
        }
        
        return line;
    }
    
    /**
     * Fix ClassCastException by adding type checks
     */
    private FixResult fixClassCast(LogAnomaly anomaly, String sourceCode) {
        String[] lines = sourceCode.split("\n");
        int targetLine = anomaly.getLineNumber() - 1;
        
        if (targetLine < 0 || targetLine >= lines.length) {
            targetLine = findVulnerableLine(lines, "CLASS_CAST");
        }
        
        if (targetLine < 0 || targetLine >= lines.length) {
            return new FixResult(false, sourceCode, sourceCode,
                "Could not locate vulnerable line", anomaly.getSourceFile(),
                anomaly.getLineNumber(), "");
        }
        
        String originalLine = lines[targetLine];
        String fixedLine = addTypeCheck(originalLine);
        
        StringBuilder fixedCode = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == targetLine) {
                fixedCode.append(fixedLine).append("\n");
            } else {
                fixedCode.append(lines[i]).append("\n");
            }
        }
        
        String patchDiff = generateDiff(originalLine, fixedLine, targetLine + 1);
        
        return new FixResult(true, sourceCode, fixedCode.toString(),
            "Added type check to prevent ClassCastException",
            anomaly.getSourceFile(), targetLine + 1, patchDiff);
    }
    
    /**
     * Add type check before cast
     */
    private String addTypeCheck(String line) {
        // Pattern: (Type) variable
        Pattern pattern = Pattern.compile("\\(([\\w.]+)\\)\\s*(\\w+)");
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            String type = matcher.group(1);
            String variable = matcher.group(2);
            String indent = getIndentation(line);
            
            return indent + "if (" + variable + " instanceof " + type + ") {\n" +
                   indent + "    " + line.trim() + "\n" +
                   indent + "} else {\n" +
                   indent + "    // TODO: Handle incompatible type\n" +
                   indent + "    logger.warn(\"Cannot cast \" + " + variable + ".getClass().getName() + \" to " + type + "\");\n" +
                   indent + "}";
        }
        
        return line;
    }
    
    /**
     * Fix IllegalArgumentException by adding validation
     */
    private FixResult fixInvalidArgument(LogAnomaly anomaly, String sourceCode) {
        String[] lines = sourceCode.split("\n");
        int targetLine = anomaly.getLineNumber() - 1;
        
        if (targetLine < 0 || targetLine >= lines.length) {
            return new FixResult(false, sourceCode, sourceCode,
                "Line number out of range", anomaly.getSourceFile(),
                anomaly.getLineNumber(), "");
        }
        
        String originalLine = lines[targetLine];
        String fixedLine = addValidation(originalLine);
        
        StringBuilder fixedCode = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == targetLine) {
                fixedCode.append(fixedLine).append("\n");
            } else {
                fixedCode.append(lines[i]).append("\n");
            }
        }
        
        String patchDiff = generateDiff(originalLine, fixedLine, targetLine + 1);
        
        return new FixResult(true, sourceCode, fixedCode.toString(),
            "Added argument validation to prevent IllegalArgumentException",
            anomaly.getSourceFile(), anomaly.getLineNumber(), patchDiff);
    }
    
    /**
     * Add argument validation
     */
    private String addValidation(String line) {
        String indent = getIndentation(line);
        
        return indent + "// Added validation to prevent IllegalArgumentException\n" +
               indent + "if (args != null && args.length > 0) {\n" +
               indent + "    " + line.trim() + "\n" +
               indent + "} else {\n" +
               indent + "    logger.warn(\"Invalid arguments provided\");\n" +
               indent + "}";
    }
    
    /**
     * Get indentation from a line
     */
    private String getIndentation(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }
    
    /**
     * Generate a simple diff
     */
    private String generateDiff(String original, String fixed, int lineNumber) {
        return "--- Original (line " + lineNumber + ")\n" +
               "+++ Fixed\n" +
               "-" + original + "\n" +
               "+" + fixed + "\n";
    }
    
    /**
     * Apply fix to a file
     */
    public boolean applyFixToFile(FixResult fix, String filePath) {
        if (!fix.isSuccess()) {
            return false;
        }
        
        try {
            Files.writeString(Path.of(filePath), fix.getFixedCode());
            return true;
        } catch (IOException e) {
            System.err.println(RED + "Failed to apply fix to file: " + e.getMessage() + RESET);
            return false;
        }
    }
    
    /**
     * Create a patch file
     */
    public String createPatchFile(FixResult fix, String outputDir) throws IOException {
        String patchFileName = "fix_" + System.currentTimeMillis() + ".patch";
        Path patchPath = Path.of(outputDir, patchFileName);
        
        String patchContent = "diff --git a/" + fix.getFilePath() + " b/" + fix.getFilePath() + "\n" +
                             "index 0000000..1111111 100644\n" +
                             "--- a/" + fix.getFilePath() + "\n" +
                             "+++ b/" + fix.getFilePath() + "\n" +
                             "@@ -" + fix.getLineNumber() + ",1 +" + fix.getLineNumber() + ",1 @@\n" +
                             fix.getPatchDiff();
        
        Files.createDirectories(patchPath.getParent());
        Files.writeString(patchPath, patchContent);
        
        return patchPath.toString();
    }
    
    /**
     * Find a vulnerable line in the code
     */
    private int findVulnerableLine(String[] lines, String vulnerabilityType) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            switch (vulnerabilityType) {
                case "NULL_POINTER":
                    // Look for patterns like: variable.method() or return variable.method()
                    if (line.matches(".*\\w+\\.\\w+\\(.*\\).*") && 
                        !line.startsWith("//") && !line.startsWith("*")) {
                        // Skip if it already has a null check
                        if (i > 0 && lines[i-1].contains("!= null")) {
                            continue;
                        }
                        return i;
                    }
                    break;
                case "ARRAY_BOUNDS":
                    if (line.matches(".*\\w+\\[\\w+\\].*") && 
                        !line.startsWith("//") && !line.startsWith("*")) {
                        return i;
                    }
                    break;
                case "CLASS_CAST":
                    if (line.matches(".*\\([A-Z]\\w+\\)\\s*\\w+.*") && 
                        !line.startsWith("//") && !line.startsWith("*")) {
                        return i;
                    }
                    break;
            }
        }
        return -1;
    }
}
