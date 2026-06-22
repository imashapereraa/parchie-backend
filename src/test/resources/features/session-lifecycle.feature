Feature: Session lifecycle
  Anonymous clients create sessions, fetch metadata, and receive 404 for missing sessions.

  Scenario: Creating a session returns metadata with defaults
    When I create a session as "alpha"
    Then the response status is 201
    And the response has session metadata
    And the metadata says "locked" is "false"
    And the metadata says "hasPassword" is "false"
    And the expiry of "alpha" is approximately 7 days from now

  Scenario: Fetching a missing session returns 404
    When I fetch session with id "00000000-0000-0000-0000-000000000000"
    Then the response status is 404

  Scenario: Fetching an existing session returns its metadata
    Given a session "alpha" exists
    When I fetch session "alpha"
    Then the response status is 200
    And the metadata says "locked" is "false"
