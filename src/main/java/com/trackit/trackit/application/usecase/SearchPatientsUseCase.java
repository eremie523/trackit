package com.trackit.trackit.application.usecase;

import java.util.List;
import java.util.Optional;

import com.trackit.trackit.application.dto.PatientSearchItemDTO;
import com.trackit.trackit.application.dto.SearchPatientFilterDTO;
import com.trackit.trackit.application.ports.repositories.IPatientRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SearchPatientsUseCase implements IUseCase<SearchPatientFilterDTO, List<PatientSearchItemDTO>> {

    private final IPatientRepository patientRepository;

    @Inject
    public SearchPatientsUseCase(IPatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    protected SearchPatientsUseCase() {
        this.patientRepository = null;
    }

    @Override
    public Optional<List<PatientSearchItemDTO>> execute(SearchPatientFilterDTO filter) {
        if (filter == null) {
            return Optional.empty();
        }

        List<PatientSearchItemDTO> results = patientRepository.fetchAll(filter);
        return Optional.of(results);
    }
}
