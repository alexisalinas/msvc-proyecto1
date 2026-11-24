package com.banamex.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.banamex.app.enums.FormulaEnum;
import com.banamex.app.exception.BadRequestException;
import com.banamex.app.task.EvaluateFormulaTask;

/**
 * Unit tests for {@link EvaluateFormulaTask}.
 */
class EvaluateFormulaTaskTest {

    /**
     * Instance under test.
     */
    private EvaluateFormulaTask task;

    /**
     * Initializes the instance before each test.
     */
    @BeforeEach
    void setUp() {
        task = new EvaluateFormulaTask();
    }

    /**
     * Builds a formula separating each token with {@code $$}.
     *
     * @param tokens formula tokens.
     * @return the formula in the {@code $$token1$$token2$$} format.
     */
    private static String f(final String... tokens) {
        return "$$" + String.join("$$", tokens) + "$$";
    }

    /**
     * Gets the name of the {@link FormulaEnum} whose value is the given symbol.
     *
     * @param symbol operator symbol (+, -, *, /).
     * @return the matching enum name.
     */
    private static String op(final String symbol) {
        return Arrays.stream(FormulaEnum.values())
                .filter(e -> symbol.equals(e.getValue()))
                .findFirst()
                .map(FormulaEnum::getName)
                .orElseThrow(() -> new IllegalStateException(
                        "No FormulaEnum with value " + symbol));
    }

    /**
     * Tests for the {@code invoke} method.
     */
    @Nested
    @DisplayName("invoke()")
    class Invoke {

        /**
         * Verifies that two numbers are added.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void simpleAddition() throws Exception {
            // Given
            String formula = f("10", op("+"), "5");

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("15.0", result);
        }

        /**
         * Verifies that a subtraction can return a negative result.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void subtractionWithNegativeResult() throws Exception {
            // Given
            String formula = f("10", op("-"), "15");

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("-5.0", result);
        }

        /**
         * Verifies that operator precedence is respected.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void respectsOperatorPrecedence() throws Exception {
            // Given
            String formula = f("2", op("+"), "3", op("*"), "4");

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("14.0", result);
        }

        /**
         * Verifies that commas are removed from numbers.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void removesThousandsCommas() throws Exception {
            // Given
            String formula = f("1,500", op("+"), "500");

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("2000.0", result);
        }

        /**
         * Verifies that tokens starting with {@code //} are ignored.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void ignoresComments() throws Exception {
            // Given
            String formula = f("//comment", "10", op("+"), "5");

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("15.0", result);
        }

        /**
         * Verifies that large numbers are returned without scientific notation.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void largeNumbersWithoutScientificNotation() throws Exception {
            // Given
            String formula = f("100000", op("*"), "100000");

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("10000000000.0", result);
        }

        /**
         * Verifies that the result is truncated to two decimals.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void truncatesToTwoDecimals() throws Exception {
            // Given
            String formula = f("10", op("/"), "3") + "TRUNCATE$$2$$/TRUNCATE$$";

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("3.33", result);
        }

        /**
         * Verifies that the result is truncated to zero decimals.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void truncatesToZeroDecimals() throws Exception {
            // Given
            String formula = f("10", op("/"), "3") + "TRUNCATE$$0$$/TRUNCATE$$";

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("3.0", result);
        }

        /**
         * Verifies that a truncated result without a decimal point gets ".0" appended.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void truncateWithoutDecimalPointAppendsZero() throws Exception {
            // Given
            String formula = f("100000", op("*"), "100000") + "TRUNCATE$$2$$/TRUNCATE$$";

            // When
            String result = task.invoke("WEB", formula);

            // Then
            assertEquals("10000000000.0", result);
        }

        /**
         * Verifies that the ATM channel adds a thousands separator.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void atmChannelAddsThousandsSeparator() throws Exception {
            // Given
            String formula = f("1234567");

            // When
            String result = task.invoke("ATM", formula);

            // Then
            assertEquals("1,234,567.0", result);
        }

        /**
         * Verifies that the channel comparison is case-sensitive.
         *
         * @throws Exception if the evaluation fails.
         */
        @Test
        void atmChannelIsCaseSensitive() throws Exception {
            // Given
            String formula = f("1234567");

            // When
            String result = task.invoke("atm", formula);

            // Then
            assertEquals("1234567.0", result);
        }

        /**
         * Verifies that division by zero throws {@link BadRequestException}.
         */
        @Test
        void divisionByZeroThrowsBadRequest() {
            // Given
            String formula = f("10", op("/"), "0");

            // When / Then
            assertThrows(BadRequestException.class,
                    () -> task.invoke("WEB", formula));
        }

        /**
         * Verifies that an unknown token throws {@link BadRequestException}.
         */
        @Test
        void unknownTokenThrowsBadRequest() {
            // Given
            String formula = f("10", "DOES_NOT_EXIST", "5");

            // When / Then
            assertThrows(BadRequestException.class,
                    () -> task.invoke("WEB", formula));
        }

        /**
         * Verifies that a null formula throws {@link BadRequestException}.
         */
        @Test
        void nullFormulaThrowsBadRequest() {
            // Given
            String formula = null;

            // When / Then
            assertThrows(BadRequestException.class,
                    () -> task.invoke("WEB", formula));
        }

        /**
         * Verifies that an empty formula throws {@link BadRequestException}.
         */
        @Test
        void emptyFormulaThrowsBadRequest() {
            // Given
            String formula = "";

            // When / Then
            assertThrows(BadRequestException.class,
                    () -> task.invoke("WEB", formula));
        }

        /**
         * Verifies that a formula with a comparison throws {@link BadRequestException}.
         */
        @Test
        void comparisonThrowsBadRequest() {
            // Given
            String formula = f("5", FormulaEnum.GT.getName(), "3");

            // When / Then
            assertThrows(BadRequestException.class,
                    () -> task.invoke("WEB", formula));
        }
    }

    /**
     * Tests for the private {@code evalComparison} method.
     */
    @Nested
    @DisplayName("evalComparison()")
    class EvalComparison {

        /**
         * Invokes {@code evalComparison} with the built expression.
         *
         * @param op    comparison operator.
         * @param left  left expression.
         * @param right right expression.
         * @return "True" or "False".
         */
        private String compare(final FormulaEnum op,
                               final String left,
                               final String right) {
            return ReflectionTestUtils.invokeMethod(task, "evalComparison",
                    left + op.getValue() + right, op.getName());
        }

        /**
         * Verifies the greater-than operator.
         */
        @Test
        void gt() {
            // Given
            FormulaEnum op = FormulaEnum.GT;

            // When
            String greater = compare(op, "5", "3");
            String lower = compare(op, "3", "5");

            // Then
            assertEquals("True", greater);
            assertEquals("False", lower);
        }

        /**
         * Verifies the greater-than-or-equal operator.
         */
        @Test
        void gte() {
            // Given
            FormulaEnum op = FormulaEnum.GTE;

            // When
            String result = compare(op, "5", "5");

            // Then
            assertEquals("True", result);
        }

        /**
         * Verifies the less-than operator.
         */
        @Test
        void lt() {
            // Given
            FormulaEnum op = FormulaEnum.LT;

            // When
            String result = compare(op, "2", "3");

            // Then
            assertEquals("True", result);
        }

        /**
         * Verifies the less-than-or-equal operator.
         */
        @Test
        void lte() {
            // Given
            FormulaEnum op = FormulaEnum.LTE;

            // When
            String result = compare(op, "4", "3");

            // Then
            assertEquals("False", result);
        }

        /**
         * Verifies the equals operator.
         */
        @Test
        void eq() {
            // Given
            FormulaEnum op = FormulaEnum.EQ;

            // When
            String result = compare(op, "2+2", "4");

            // Then
            assertEquals("True", result);
        }

        /**
         * Verifies the not-equals operator.
         */
        @Test
        void neq() {
            // Given
            FormulaEnum op = FormulaEnum.NEQ;

            // When
            String result = compare(op, "2", "3");

            // Then
            assertEquals("True", result);
        }

        /**
         * Verifies that an exception is thrown when the operator is missing.
         */
        @Test
        void operatorNotFound() {
            // Given
            String expression = "5 3";

            // When / Then
            assertThrows(IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(task, "evalComparison",
                            expression, FormulaEnum.GT.getName()));
        }

        /**
         * Verifies that an exception is thrown when one side is empty.
         */
        @Test
        void emptySide() {
            // Given
            String expression = FormulaEnum.GT.getValue() + "3";

            // When / Then
            assertThrows(IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(task, "evalComparison",
                            expression, FormulaEnum.GT.getName()));
        }

        /**
         * Verifies that an exception is thrown when the right side is empty.
         */
        @Test
        void emptyRightSide() {
            // Given
            String expression = "5" + FormulaEnum.GT.getValue();

            // When / Then
            assertThrows(IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(task, "evalComparison",
                            expression, FormulaEnum.GT.getName()));
        }

        /**
         * Verifies that a non-comparison operator throws an exception.
         */
        @Test
        void nonComparisonOperatorThrowsException() {
            // Given
            String expression = "2+3";
            String operatorName = op("+");

            // When / Then
            assertThrows(IllegalArgumentException.class,
                    () -> ReflectionTestUtils.invokeMethod(task, "evalComparison",
                            expression, operatorName));
        }
    }

    /**
     * Tests for the {@code eval} method.
     */
    @Nested
    @DisplayName("eval()")
    class Eval {

        /**
         * Verifies the evaluation of different arithmetic expressions.
         *
         * @param expr     expression to evaluate.
         * @param expected expected result.
         */
        @ParameterizedTest
        @CsvSource({
                "2+3,        5.0",
                "2+3*4,      14.0",
                "(2+3)*4,    20.0",
                "10/4,       2.5",
                "2^3,        8.0",
                "-5+2,       -3.0",
                "+7,         7.0",
                "' 1 + 1 ',  2.0",
                "'.5',       0.5"
        })
        void evaluatesExpressions(final String expr, final double expected) {
            // Given: the expr and expected parameters

            // When
            double result = task.eval(expr);

            // Then
            assertEquals(expected, result, 0.0001);
        }

        /**
         * Verifies that an incomplete expression throws an exception.
         */
        @Test
        void incompleteExpressionThrowsException() {
            // Given
            String expression = "2+";

            // When / Then
            assertThrows(RuntimeException.class, () -> task.eval(expression));
        }

        /**
         * Verifies that an unexpected character throws an exception.
         */
        @Test
        void unexpectedCharacterThrowsException() {
            // Given
            String expression = "2a";

            // When / Then
            assertThrows(RuntimeException.class, () -> task.eval(expression));
        }

        /**
         * Verifies that the percentage operator calculates the percentage of a value.
         */
        @Test
        void pctCalculatesPercentage() {
            // Given
            String expression = "50%10";

            // When
            double result = task.eval(expression);

            // Then
            assertEquals(5.0, result);
        }
    }

    /**
     * Tests for the private {@code formatForChannel} method.
     */
    @Nested
    @DisplayName("formatForChannel()")
    class FormatForChannel {

        /**
         * Verifies the ATM format for a value without a decimal part.
         */
        @Test
        void atmValueWithoutDecimalPart() {
            // Given
            String value = "1234567";

            // When
            String result = ReflectionTestUtils.invokeMethod(task,
                    "formatForChannel", value, "ATM");

            // Then
            assertEquals("1,234,567", result);
        }
    }

    /**
     * Tests for the {@code isNumeric} method.
     */
    @Nested
    @DisplayName("isNumeric()")
    class IsNumeric {

        /**
         * Verifies that numeric values return {@code true}.
         *
         * @param value value to validate.
         */
        @ParameterizedTest
        @ValueSource(strings = {"123", "12.5", " 42 "})
        void validNumbers(final String value) {
            // Given: the value parameter

            // When
            boolean result = task.isNumeric(value);

            // Then
            assertTrue(result);
        }

        /**
         * Verifies that non-numeric values return {@code false}.
         *
         * @param value value to validate.
         */
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "abc", "1a"})
        void invalidValues(final String value) {
            // Given: the value parameter

            // When
            boolean result = task.isNumeric(value);

            // Then
            assertFalse(result);
        }
    }
}
