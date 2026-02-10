package org.stella;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

class MainTest {

    // Тест для хороших программ (ожидаем код возврата 0)
    @ParameterizedTest(name = "{index} Well-typed: {0}")
    @MethodSource("wellTypedFiles")
    void testWellTyped(String filepath) throws Exception {
        runTest(filepath, 0, null);
    }

    // Тест для плохих программ (ожидаем код возврата 1 и текст ошибки)
    @ParameterizedTest(name = "{index} Ill-typed: {0}")
    @MethodSource("illTypedFiles")
    void testIllTyped(String filepath) throws Exception {
        String expectedError = readExpectedErrorFromFile(filepath);
        runTest(filepath, 1, expectedError);
    }

    // Общий метод запуска
    private void runTest(String filepath, int expectedExitCode, String expectedErrorMessage) throws Exception {
        // Создаем "ловушку" для ошибок, чтобы прочитать вывод программы
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream errStream = new PrintStream(errBuffer);

        // Открываем файл теста
        try (FileInputStream fileIn = new FileInputStream(filepath)) {

            // ВАЖНО: Вызываем Main.run, а не main!
            int actualExitCode = Main.compile(fileIn, System.out, errStream);

            // Проверяем код возврата
            assertEquals(expectedExitCode, actualExitCode,
                    "Exit code mismatch for " + filepath + ".\nOutput:\n" + errBuffer.toString());

            // Если ожидаем ошибку, проверяем текст
            if (expectedErrorMessage != null) {
                String actualOutput = errBuffer.toString();
                assertTrue(actualOutput.contains(expectedErrorMessage),
                        "File: " + filepath + "\n" +
                                "Expected error to contain: " + expectedErrorMessage + "\n" +
                                "Actual output:\n" + actualOutput);
            }
        }
    }

    // --- Вспомогательные методы (поиск файлов) ---

    static Stream<Arguments> wellTypedFiles() throws IOException {
        return Files.list(Paths.get("tests/well-typed")) // Проверьте путь!
                .filter(p -> p.toString().endsWith(".stella"))
                .map(p -> Arguments.of(p.toString()));
    }

    static Stream<Arguments> illTypedFiles() throws IOException {
        return Files.list(Paths.get("tests/ill-typed")) // Проверьте путь!
                .filter(p -> p.toString().endsWith(".stella"))
                .map(p -> Arguments.of(p.toString()));
    }

    private String readExpectedErrorFromFile(String filepath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filepath));
        if (lines.isEmpty()) fail("File empty: " + filepath);

        String lastLine = lines.get(lines.size() - 1);
        int idx = lastLine.lastIndexOf("///");

        if (idx == -1) {
            // Если комментария нет, тест не может проверить ошибку
            fail("No expected error comment (//ERROR...) found in last line of " + filepath);
        }

        return lastLine.substring(idx + 3).trim();
    }
}