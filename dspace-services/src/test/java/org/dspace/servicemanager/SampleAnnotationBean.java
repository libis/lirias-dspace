/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.servicemanager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.dspace.servicemanager.example.ConcreteExample;
import org.dspace.servicemanager.example.ServiceExample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This bean is a simple example of a bean which is annotated as a Spring Bean
 * and should be found when the AC starts up.
 *
 * @author Aaron Zeckoski (azeckoski @ gmail.com)
 */
@Service
public class SampleAnnotationBean {

    public int initCounter = 0;

    @PostConstruct
    public void init() {
        initCounter++;
    }

    @PreDestroy
    public void shutdown() {
        initCounter++;
    }

    private ServiceExample serviceExample;

    @Autowired(required = true)
    public void setServiceExample(ServiceExample serviceExample) {
        this.serviceExample = serviceExample;
    }

    private ConcreteExample concreteExample;

    @Autowired(required = true)
    public void setConcreteExample(ConcreteExample concreteExample) {
        this.concreteExample = concreteExample;
    }

    public String getExampleName() {
        return serviceExample.getName();
    }

    public String getOtherName() {
        return serviceExample.getOtherName();
    }

    public String getConcreteName() {
        return concreteExample.getName();
    }

    private String value = null;

    public void setSampleValue(String value) {
        this.value = value;
    }

    public String getSampleValue() {
        return value;
    }

}
