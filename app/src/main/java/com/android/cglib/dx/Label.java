
package com.android.cglib.dx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.android.cglib.dx.rop.code.BasicBlock;
import com.android.cglib.dx.rop.code.Insn;
import com.android.cglib.dx.rop.code.InsnList;
import com.android.cglib.dx.util.IntList;

public final class Label {

    final List<Insn> instructions = new ArrayList<Insn>();

    Code code;

    boolean marked = false;

    List<Label> catchLabels = Collections.emptyList();

    Label primarySuccessor;

    Label alternateSuccessor;

    int id = -1;

    public Label() {}

    boolean isEmpty() {
        return instructions.isEmpty();
    }

    void compact() {
        for (int i = 0; i < catchLabels.size(); i++) {
            while (catchLabels.get(i).isEmpty()) {
                catchLabels.set(i, catchLabels.get(i).primarySuccessor);
            }
        }
        while (primarySuccessor != null && primarySuccessor.isEmpty()) {
            primarySuccessor = primarySuccessor.primarySuccessor;
        }
        while (alternateSuccessor != null && alternateSuccessor.isEmpty()) {
            alternateSuccessor = alternateSuccessor.primarySuccessor;
        }
    }

    BasicBlock toBasicBlock() {
        InsnList result = new InsnList(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            result.set(i, instructions.get(i));
        }
        result.setImmutable();

        int primarySuccessorIndex = -1;
        IntList successors = new IntList();
        for (Label catchLabel : catchLabels) {
            successors.add(catchLabel.id);
        }
        if (primarySuccessor != null) {
            primarySuccessorIndex = primarySuccessor.id;
            successors.add(primarySuccessorIndex);
        }
        if (alternateSuccessor != null) {
            successors.add(alternateSuccessor.id);
        }
        successors.setImmutable();

        return new BasicBlock(id, result, successors, primarySuccessorIndex);
    }
}
