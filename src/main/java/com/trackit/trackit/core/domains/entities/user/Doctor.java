package com.trackit.trackit.core.domains.entities.user;

import com.trackit.trackit.core.domains.entities.user.dto.CreateDoctorDTO;

public class Doctor {
    public final String userId;

    protected Doctor(CreateDoctorDTO input) {
        this.userId = input.userId();
    }

    public void main() {
        System.out.print("This is a test");
    }
}
