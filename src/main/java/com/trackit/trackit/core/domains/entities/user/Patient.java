package com.trackit.trackit.core.domains.entities.user;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.UUID;

import com.trackit.trackit.core.domains.entities.user.dto.CreatePatientDTO;
import com.trackit.trackit.core.domains.entities.user.dto.ReconstructPatientDTO;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Address;
import com.trackit.trackit.core.domains.entities.user.valueObjects.BloodType;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Genotype;

public class Patient {
    public final String id;
    public final String userId;
    public boolean isMarried;
    public BloodType bloodType;
    public Genotype genotype;
    public final Instant dob;
    public final int age;
    public final Address address;
    public final boolean isNew;

    private Patient(CreatePatientDTO input) {
        this.id = UUID.randomUUID().toString();
        this.userId = input.userId();
        this.dob = input.dob();
        this.isMarried = input.isMarried();
        this.genotype = input.genotype();
        this.bloodType = input.bloodType();
        LocalDate dob = this.dob.atZone(ZoneId.of("UTC")).toLocalDate();
        this.age = Period.between(dob, LocalDate.now(ZoneId.of("UTC"))).getYears();
        this.address = input.address();
        this.isNew = true;
    }

    private Patient(ReconstructPatientDTO input) {
        this.id = input.id();
        this.userId = input.userId();
        this.dob = input.dob();
        this.isMarried = input.isMarried();
        this.genotype = input.genotype();
        this.bloodType = input.bloodType();
        this.age = input.age();
        this.address = input.address();
        this.isNew = false;
    }

    public static Patient create(CreatePatientDTO input) {
        return new Patient(input);
    }

    public static Patient reconstruct(ReconstructPatientDTO input) {
        return new Patient(input);
    }

}