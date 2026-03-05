# Architectural Design Records

## ADR-001: Use Firebase Auth for Anonymous sign-in 
### Context: US 01.07.01

### Rationale: 
- Keep authentication logic outside of application code 
- get to use already existing authentication logic from Firebase
- easy to upgrade authentication practices later through Firebase console


## ADR-002: Firebase is the only source of truth for collections
### Context: Where should we store the ArrayList<> of Entrants for Waitlist and EventPool?

### Rationale: 
- Avoid mismatched collections when a Storage model is called to modify one of the collections, and the subsequent Domain model has not been updated. 
    - In turn, we choose not to update the Domain model at all. 
    - The domain models will only contain semi-persistent user-set metadata.
- Move all retrieval to the Storage class and not strewn between Domains and Storage.