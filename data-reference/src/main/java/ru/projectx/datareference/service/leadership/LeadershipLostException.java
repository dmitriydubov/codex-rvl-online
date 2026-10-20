package ru.projectx.datareference.service.leadership;

public class LeadershipLostException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LeadershipLostException(String instanceId) {
        super("экземпляр " + instanceId + " больше не держит блокировку ведущего");
    }
}
