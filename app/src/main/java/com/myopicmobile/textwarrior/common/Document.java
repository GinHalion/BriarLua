package com.myopicmobile.textwarrior.common;

import java.util.ArrayList;

public class Document extends TextBuffer {

    private boolean _isWordWrap = false;

    private TextFieldMetrics _metrics;

    private ArrayList<Integer> _rowTable;

    public Document(TextFieldMetrics metrics) {
        super();
        _metrics = metrics;
        resetRowTable();
    }

    public void setText(CharSequence text) {
        int lineCount = 1;
        int len = text.length();
        char[] ca = new char[TextBuffer.memoryNeeded(len)];
        for (int i = 0; i < len; i++) {
            ca[i] = text.charAt(i);
            if (text.charAt(i) == '\n')
                lineCount++;
        }
        setBuffer(ca, len, lineCount);
    }

    private void resetRowTable() {
        ArrayList<Integer> rowTable = new ArrayList<Integer>();
        rowTable.add(0);
        _rowTable = rowTable;
    }

    public void setMetrics(TextFieldMetrics metrics) {
        _metrics = metrics;
    }

    public void setWordWrap(boolean enable) {
        if (enable && !_isWordWrap) {
            _isWordWrap = true;
            analyzeWordWrap();
        } else if (!enable && _isWordWrap) {
            _isWordWrap = false;
            analyzeWordWrap();
        }
    }

    public boolean isWordWrap() {
        return _isWordWrap;
    }


    @Override
    public synchronized void delete(int charOffset, int totalChars, long timestamp, boolean undoable) {
        super.delete(charOffset, totalChars, timestamp, undoable);

        int startRow = findRowNumber(charOffset);
        int analyzeEnd = findNextLineFrom(charOffset);
        updateWordWrapAfterEdit(startRow, analyzeEnd, -totalChars);
    }

    @Override
    public synchronized void insert(char[] c, int charOffset, long timestamp, boolean undoable) {
        super.insert(c, charOffset, timestamp, undoable);

        int startRow = findRowNumber(charOffset);
        int analyzeEnd = findNextLineFrom(charOffset + c.length);
        updateWordWrapAfterEdit(startRow, analyzeEnd, c.length);
    }

    @Override
    synchronized void shiftGapStart(int displacement) {
        super.shiftGapStart(displacement);

        if (displacement != 0) {
            int startOffset = (displacement > 0)
                    ? _gapStartIndex - displacement
                    : _gapStartIndex;
            int startRow = findRowNumber(startOffset);
            int analyzeEnd = findNextLineFrom(_gapStartIndex);
            updateWordWrapAfterEdit(startRow, analyzeEnd, displacement);
        }
    }

    private int findNextLineFrom(int charOffset) {
        int lineEnd = logicalToRealIndex(charOffset);

        while (lineEnd < _contents.length) {
            if (lineEnd == _gapStartIndex) {
                lineEnd = _gapEndIndex;
            }

            if (_contents[lineEnd] == Language.NEWLINE ||
                    _contents[lineEnd] == Language.EOF) {
                break;
            }

            ++lineEnd;
        }

        return realToLogicalIndex(lineEnd) + 1;
    }

    private void updateWordWrapAfterEdit(int startRow, int analyzeEnd, int delta) {
        if (startRow < 0) {
            startRow = 0;
        } else if (startRow > 0) {
            --startRow;
        }
        int analyzeStart = _rowTable.get(startRow);

        removeRowMetadata(startRow + 1, analyzeEnd - delta);
        adjustOffsetOfRowsFrom(startRow + 1, delta);
        analyzeWordWrap(startRow + 1, analyzeStart, analyzeEnd);
    }

    private void removeRowMetadata(int fromRow, int endOffset) {
        while (fromRow < _rowTable.size() &&
                _rowTable.get(fromRow) <= endOffset) {
            _rowTable.remove(fromRow);
        }
    }

    private void adjustOffsetOfRowsFrom(int fromRow, int offset) {
        for (int i = fromRow; i < _rowTable.size(); ++i) {
            _rowTable.set(i, _rowTable.get(i) + offset);
        }
    }

    public void analyzeWordWrap() {

        resetRowTable();

        if (_isWordWrap && !hasMinimumWidthForWordWrap()) {
            if (_metrics.getRowWidth() > 0) {
                TextWarriorException.fail("Text field has non-zero width but still too small for word wrap");
            }
            return;
        }

        analyzeWordWrap(1, 0, getTextLength());
    }

    private boolean hasMinimumWidthForWordWrap() {
        final int maxWidth = _metrics.getRowWidth();
        return (maxWidth >= 2 * _metrics.getAdvance('M'));
    }

    private void analyzeWordWrap(int rowIndex, int startOffset, int endOffset) {
        if (!_isWordWrap) {
            int offset = logicalToRealIndex(startOffset);
            int end = logicalToRealIndex(endOffset);
            ArrayList<Integer> rowTable = new ArrayList<Integer>();

            while (offset < end) {
                if (offset == _gapStartIndex) {
                    offset = _gapEndIndex;
                }
                char c = _contents[offset];
                if (c == Language.NEWLINE) {
                    rowTable.add(realToLogicalIndex(offset) + 1);
                }
                ++offset;

            }
            _rowTable.addAll(rowIndex, rowTable);
            return;
        }
        if (!hasMinimumWidthForWordWrap()) {
            TextWarriorException.fail("Not enough space to do word wrap");
            return;
        }

        ArrayList<Integer> rowTable = new ArrayList<Integer>();
        int offset = logicalToRealIndex(startOffset);
        int end = logicalToRealIndex(endOffset);
        int potentialBreakPoint = startOffset;
        int wordExtent = 0;
        final int maxWidth = _metrics.getRowWidth();
        int remainingWidth = maxWidth;

        while (offset < end) {
            if (offset == _gapStartIndex) {
                offset = _gapEndIndex;
            }

            char c = _contents[offset];
            wordExtent += _metrics.getAdvance(c);

            boolean isWhitespace = (c == ' ' || c == Language.TAB
                    || c == Language.NEWLINE || c == Language.EOF);

            if (isWhitespace) {
                if (wordExtent <= remainingWidth) {
                    remainingWidth -= wordExtent;
                } else if (wordExtent > maxWidth) {
                    int current = logicalToRealIndex(potentialBreakPoint);
                    remainingWidth = maxWidth;

                    if (potentialBreakPoint != startOffset && (rowTable.isEmpty() ||
                            potentialBreakPoint != rowTable.get(rowTable.size() - 1))) {
                        rowTable.add(potentialBreakPoint);
                    }

                    while (current <= offset) {
                        if (current == _gapStartIndex) {
                            current = _gapEndIndex;
                        }

                        int advance = _metrics.getAdvance(_contents[current]);
                        if (advance > remainingWidth) {
                            rowTable.add(realToLogicalIndex(current));
                            remainingWidth = maxWidth - advance;
                        } else {
                            remainingWidth -= advance;
                        }

                        ++current;
                    }
                } else {
                    rowTable.add(potentialBreakPoint);
                    remainingWidth = maxWidth - wordExtent;
                }

                wordExtent = 0;
                potentialBreakPoint = realToLogicalIndex(offset) + 1;
            }

            if (c == Language.NEWLINE) {
                rowTable.add(potentialBreakPoint);
                remainingWidth = maxWidth;
            }

            ++offset;
        }

        _rowTable.addAll(rowIndex, rowTable);
    }

    public String getRow(int rowNumber) {

        int rowSize = getRowSize(rowNumber);
        if (rowSize == 0) {
            return new String();
        }

        int startIndex = _rowTable.get(rowNumber);
        return subSequence(startIndex, rowSize).toString();
    }

    public int getRowSize(int rowNumber) {

        if (isInvalidRow(rowNumber)) {
            return 0;
        }

        if (rowNumber != (_rowTable.size() - 1)) {
            return _rowTable.get(rowNumber + 1) - _rowTable.get(rowNumber);
        } else {
            return getTextLength() - _rowTable.get(rowNumber);
        }
    }

    public int getRowCount() {

        return _rowTable.size();
    }

    public int getRowOffset(int rowNumber) {


        if (isInvalidRow(rowNumber)) {
            return -1;
        }

        return _rowTable.get(rowNumber);
    }

    public int findRowNumber(int charOffset) {

        if (!isValid(charOffset)) {
            return -1;
        }

        int right = _rowTable.size() - 1;
        int left = 0;
        while (right >= left) {
            int mid = (left + right) / 2;
            int nextLineOffset = ((mid + 1) < _rowTable.size()) ? _rowTable.get(mid + 1) : getTextLength();
            if (charOffset >= _rowTable.get(mid) && charOffset < nextLineOffset) {
                return mid;
            }

            if (charOffset >= nextLineOffset) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return -1;
    }


    protected boolean isInvalidRow(int rowNumber) {
        return rowNumber < 0 || rowNumber >= _rowTable.size();
    }


    public static interface TextFieldMetrics {
        public int getAdvance(char c);

        public int getRowWidth();
    }
}
