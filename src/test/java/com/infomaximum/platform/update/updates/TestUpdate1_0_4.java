package com.infomaximum.platform.update.updates;

import com.infomaximum.database.domainobject.Transaction;
import com.infomaximum.platform.update.TestSubsystem;
import com.infomaximum.platform.update.UpdateTask;
import com.infomaximum.platform.update.annotation.Update;

@Update(
        componentUUID = TestSubsystem.UUID,
        version = "1.0.4.x",
        previousVersion = "1.0.3.x"
)
public class TestUpdate1_0_4 extends UpdateTask<TestSubsystem> {

    public TestUpdate1_0_4(TestSubsystem subsystem) {
        super(subsystem);
    }

    @Override
    public void updateComponent(Transaction transaction) {

    }
}