package ru.projectx.contracts.entity.payment;

enum InvoiceMark {
    NONE,
    REPEATS_RESET {
        @Override
        InvoiceProgress record(InvoiceProgress moved) {
            return new InvoiceProgress(moved.state(), 0, moved.lostReplyReported(), moved.linked());
        }
    },
    LOST_REPLY_REPORTED {
        @Override
        boolean refuses(InvoiceProgress current) {
            return current.lostReplyReported() || current.linked();
        }

        @Override
        InvoiceProgress record(InvoiceProgress moved) {
            return new InvoiceProgress(moved.state(), moved.fixRepeats(), true, moved.linked());
        }
    };

    boolean refuses(InvoiceProgress current) {
        return false;
    }

    InvoiceProgress record(InvoiceProgress moved) {
        return moved;
    }
}
