package uk.gov.wildfyre.gpcadaptor.dao;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import org.hl7.fhir.dstu3.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class PatientDao implements IPatient {

    @Autowired
    IOrganisation organisationDao;

    @Autowired
    IPractitioner practitionerDao;

    @Autowired
    ILocation locationDao;

    @Override
    public Patient read(IGenericClient client, IdType internalId) {

        Bundle result = client.search()
                .forResource(Patient.class)
                .where(new TokenClientParam("identifier").exactly().systemAndCode("https://fhir.nhs.uk/Id/nhs-number", internalId.getIdPart()))
                .returnBundle(Bundle.class)
                .execute();

        for(Bundle.BundleEntryComponent entry : result.getEntry()) {
            if (entry.getResource() instanceof Patient) {
                Patient patient = (Patient) entry.getResource();

                processPractice(patient, client);
                processGP(patient, client);

                return  patient;
            }
        }

        return null;
    }

    private void processGP(Patient patient, IGenericClient client) {
        if (patient.hasGeneralPractitioner()) {
            Practitioner gp = practitionerDao.read(client,new IdType().setValue(patient.getGeneralPractitionerFirstRep().getReference()));
            if (gp !=null) {
                Reference ref = patient.getGeneralPractitionerFirstRep();
                if (gp.hasIdentifier()) {
                    ref.setIdentifier(gp.getIdentifierFirstRep());
                }
                if (gp.hasName())
                    ref.setDisplay(gp.getNameFirstRep().getNameAsSingleString());
            }
        }
    }

    private void processPractice(Patient patient, IGenericClient client) {
        if (patient.hasManagingOrganization()) {
            Organization surgery = organisationDao.read(client,new IdType().setValue(patient.getManagingOrganization().getReference()));
            if (surgery != null) {
                Reference ref =patient.getManagingOrganization();
                if (surgery.hasIdentifier()) {
                    ref.setIdentifier(surgery.getIdentifierFirstRep());
                }
                ref.setDisplay(surgery.getName());
            }
        }
    }


}
