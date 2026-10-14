/**
 * Browser-assisted application contract. There is deliberately no unattended submit operation: by default the human
 * presses the final submit button and the adapter only observes the outcome (ADR-0006). Adapters must stop and hand
 * control back on CAPTCHA, MFA, login walls or access restrictions — never bypass them.
 */
package com.jobsapply.spi.application;
