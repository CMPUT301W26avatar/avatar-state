# User Stories

## US 01.07.01 As an entrant, I want to be identified by my device, so that I don't have to use a username and password.

### Status: To Review
- Stores unique user ID into firebase upon user sign-in.<br>
#### Tested for: <br>
    - user appears in firebase <br>
    - multiple sign-in attempts all have the same uuid <br>


## US 01.02.01 As an entrant, I want to provide my personal information such as name, email and optional phone number in the app

### Status: To Review
- UserProfileActivity page with text fields for setting / updating user credentials <br> 
#### Tested for:
    - activity opens <br>
    - back button returns to MainActivity <br>
    - fields take input <br>
    - the correct fields are stored <br>

## US 01.02.02 As an entrant I want to update information such as name, email and contact information on my profile

### Status: To Review
- Same as US 01.02.01, refer to US 01.02.01. <br>

## US 01.06.02 As an entrant I want to be able to be sign up for an event by from the event details.

### Status: To Review
- Can enroll and unenroll from an event from the EventDetailsActivity page via ListEventsActivity<br>
#### Tested for:<br>
    - activity opens<br>
    - back button returns to ListEventsActivity<br>
    - enroll adds a new entrant to the event pool<br>
    - unenroll deletes the current entrant from the event pool<br>
    - enroll and unenroll toggle back and forth based on whether the entrant is enrolled or not<br>


## US 02.01.01 As an organizer I want to create a new event and generate a unique promotional QR code that links to the event description and event poster in the app.

### Status: In Progress 
- Creates a new event from the CreateEventActivity and stores event fields in Firebase<br>
#### Tested for:
    - activity opens<br>
    - back button returns to MainActivity<br>
    - test fields show set values after being entered and before saving the event<br> 
    - test enforcement of required fields<br>
    - test the creation and retrieval of a valid event<br> 
    - test enforcement of registration start day being less than registration end day<br> 
#### Missing: 
    - QR code logic<br>
    - Event Details page missing poster logic<br>

## US 02.01.04 As an organizer, I want to set a registration period.

### Status: In Progress 
- Ability to set regisration period in CreateEventsActivity<br>
    - seperate drawDate attribute<br>
#### Tested for:
    - Firebase update and retrieval
    - Enforcement of regisration start < registration end
#### Missing: <br>
    - UpdateEventActivity : right now the registration period attributes are only ever set on create<br> 


## US 02.03.01 As an organizer I want to OPTIONALLY limit the number of entrants who can join my waiting list..

### Status: In Progress 
- Ability to set waitlist capacity in CreateEventsActivity
- Only has been tested for the waitlist capacity Integer being properly updated / retrieved.
#### Missing: <br>
    - UpdateEventActivity : right now the waitlist capacity attribute is only ever set on create
