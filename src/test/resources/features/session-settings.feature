Feature: Updating session settings
  Owners adjust lock state, password, and expiry via PATCH.

  Scenario: Locking a session blocks future state writes
    Given a session "alpha" exists
    When I PATCH "alpha" with locked=true
    Then the response status is 200
    And the metadata says "locked" is "true"
    When I PUT the bytes 0x01 to the encrypted state of "alpha"
    Then the response status is 403

  Scenario: Setting a password reports hasPassword=true
    Given a session "alpha" exists
    When I PATCH "alpha" with password "hunter2"
    Then the response status is 200
    And the metadata says "hasPassword" is "true"

  Scenario: PATCHing an unknown session is 404
    When I PATCH session id "00000000-0000-0000-0000-000000000000" with locked=true
    Then the response status is 404
