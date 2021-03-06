package uk.gov.wildfyre.gpcadaptor.dao;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.dstu3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.wildfyre.gpcadaptor.support.StructuredRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class MedicationRequestDao implements IMedicationRequest {

    private static final Logger log = LoggerFactory.getLogger(MedicationRequestDao.class);


    @Override
    public List<Resource> search(IGenericClient client, ReferenceParam patient, TokenParam status) {

        if (patient == null) {
            return Collections.emptyList();
        }


        log.info(patient.getIdPart() );
        log.info(patient.getValue() );
        log.info(patient.getChain() );

        Parameters parameters = StructuredRecord.getStructuredRecordParameters(patient.getValue(),false, true, new DateType(1980, 5, 5));
        Bundle result = client.operation().onType(Patient.class)
                .named("$gpc.getstructuredrecord")
                .withParameters(parameters)
                .returnResourceType(Bundle.class)
                .execute();
        return extractMedicationRequest(result);
    }

    public List<Resource> extractMedicationRequest(Bundle result) {
        List<Resource> medications = new ArrayList<>();

        for(Bundle.BundleEntryComponent entry : result.getEntry()) {
            if (entry.getResource() instanceof MedicationRequest) {
                MedicationRequest prescription = (MedicationRequest) entry.getResource();


                processMedicationReference(prescription, result);

                if (prescription.hasMedicationReference()) {
                    Medication medication = getMedication(result, prescription.getMedicationReference());
                    if (medication != null && medication.hasCode()) {
                        prescription.setMedication(
                                medication.getCode()
                        );
                    }
                }

                if (prescription.hasSubject()) {
                    Patient patient = getPatient(result, prescription.getSubject());
                    if (patient != null && patient.hasIdentifier() && patient.getIdentifierFirstRep().getSystem().equals("https://fhir.nhs.uk/Id/nhs-number")) {
                        prescription.getSubject().setIdentifier(patient.getIdentifierFirstRep());
                        prescription.getSubject().setReference(null);
                    }
                }
                if (prescription.hasRecorder()) {
                    Practitioner practitioner = getPractitioner(result, prescription.getRecorder());
                    if (practitioner != null && practitioner.hasIdentifier()) {
                        prescription.getRecorder().setIdentifier(practitioner.getIdentifierFirstRep());
                        prescription.getRecorder().setReference(null);
                    }
                }

                if (prescription.hasDispenseRequest() && prescription.getDispenseRequest().hasPerformer()) {
                    Organization organization = getOrganization(result, prescription.getDispenseRequest().getPerformer());
                    if (organization != null && organization.hasIdentifier()) {
                        prescription.getDispenseRequest().getPerformer().setIdentifier(organization.getIdentifierFirstRep());
                        prescription.getDispenseRequest().getPerformer().setReference(null);
                    }
                }
                if (prescription.hasRecorder() && prescription.getRecorder().getDisplay() == null) {
                    // Attempt to make the MedicationRequest more useful to calling systems.
                    Practitioner practitioner = getPractitioner(prescription.getRecorder(),result);
                    if (practitioner != null && practitioner.hasName()) {
                         prescription.getRecorder().setDisplay(practitioner.getNameFirstRep().getNameAsSingleString());
                    }

                }
                medications.add(prescription);
            }
        }
        return medications;
    }

    private Medication getMedication(Bundle result, Reference reference) {
        for (Bundle.BundleEntryComponent entry : result.getEntry()) {

            if (entry.getResource() instanceof Medication) {
                Medication medication = (Medication) entry.getResource();
                log.info("med id - " + medication.getId());
                if (reference.hasReference() && medication.getId().contains(reference.getReference()) ) {
                   return medication;

                }
            }

        }
        return null;
    }

    private Patient getPatient(Bundle result, Reference reference) {
        for (Bundle.BundleEntryComponent entry : result.getEntry()) {

            if (entry.getResource() instanceof Patient) {
                Patient patient = (Patient) entry.getResource();
                if (reference.hasReference() && patient.getId().contains(reference.getReference()) ) {
                    return patient;
                }
            }
        }
        return null;
    }

    private Practitioner getPractitioner(Bundle result, Reference reference) {
        for (Bundle.BundleEntryComponent entry : result.getEntry()) {

            if (entry.getResource() instanceof Practitioner) {
                Practitioner practitioner = (Practitioner) entry.getResource();
                if (reference.hasReference() && practitioner.getId().contains(reference.getReference()) ) {
                    return practitioner;
                }
            }
        }
        return null;
    }

    private Organization getOrganization(Bundle result, Reference reference) {
        for (Bundle.BundleEntryComponent entry : result.getEntry()) {

            if (entry.getResource() instanceof Organization) {
                Organization organization = (Organization) entry.getResource();
                if (reference.hasReference() && organization.getId().contains(reference.getReference()) ) {
                    return organization;
                }
            }
        }
        return null;
    }

    private void processMedicationReference(MedicationRequest prescription, Bundle result) {
        if (prescription.hasMedicationReference()
                && prescription.getMedicationReference().getDisplay() == null) {
            // Attempt to make the MedicationRequest more useful to calling systems.
            Medication medication = getMedication(prescription,result);
            if (medication != null && medication.hasCode()) {
                if (medication.getCode().hasCoding()) {
                    prescription.getMedicationReference().setDisplay(medication.getCode().getCoding().get(0).getDisplay());
                }
                else {
                    prescription.getMedicationReference().setDisplay(medication.getCode().getText());

                }

            }
        }
    }


    private Medication getMedication(MedicationRequest prescription, Bundle result) {

        for (Bundle.BundleEntryComponent entry : result.getEntry()) {
            if (entry.getResource() instanceof Medication) {
                Medication med = (Medication) entry.getResource();
                if (prescription.hasMedicationReference() &&
                    prescription.getMedicationReference().getReference().equals(med.getId())) {
                        return med;

                }
            }
            }

        return null;
    }

    private Practitioner getPractitioner(Reference reference, Bundle result) {

        for (Bundle.BundleEntryComponent entry : result.getEntry()) {
            if (entry.getResource() instanceof Practitioner) {

                Practitioner prac = (Practitioner) entry.getResource();

                if (reference.hasReference() &&
                    reference.getReference().equals("Practitioner/"+ prac.getIdElement().getIdPart())) {
                        return prac;
                }

            }
        }

        return null;
    }


}
