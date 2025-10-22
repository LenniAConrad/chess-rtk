package test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import application.Config;
import chess.core.Setup;
import chess.debug.Printer;
import chess.io.Converter;
import chess.uci.Filter;

// add documentation
public class Tests {
    public static void main(String[] args) {
        Printer.board(Setup.getRandomChess960());
        Printer.testPerft();
    }

    public static void convert() throws IOException {
        Path inDir = Paths.get("/home/lennart/Backup/Chess Data/1M_STACK");
        Path outDir = Paths.get("/home/lennart/Backup/Chess Data/1M_STACK_PLAIN_2");
        Files.createDirectories(outDir); // ensure target dir exists


        
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(inDir, "*.json")) {
            for (Path p : ds) {
                String base = p.getFileName().toString();
                int dot = base.lastIndexOf('.');
                String outName = (dot >= 0 ? base.substring(0, dot) : base) + ".plain";
                Path out = outDir.resolve(outName);

                System.out.println(p + " -> " + out);
                Filter arguments = Filter.builder().addLeaf(Config.getPuzzleQuality()).addLeaf(Filter.builder().gate(Filter.Gate.OR).addLeaf(Config.getPuzzleWinning()).addLeaf(Config.getPuzzleDrawing()).build()).build();
                Converter.recordToPlain(true, arguments, p, out);
            }
        }
    }
}
