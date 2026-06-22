Feature: Encrypted session state
  The server stores an opaque encrypted blob per session and round-trips it byte-for-byte.

  Scenario: GET state on a fresh session returns 204
    Given a session "alpha" exists
    When I GET the encrypted state of "alpha"
    Then the response status is 204

  Scenario: PUT then GET round-trips the bytes
    Given a session "alpha" exists
    When I PUT the bytes 0x01,0x02,0x03,0xFF to the encrypted state of "alpha"
    Then the response status is 204
    When I GET the encrypted state of "alpha"
    Then the response status is 200
    And the response bytes are 0x01,0x02,0x03,0xFF

  Scenario: PUT to a locked session is forbidden
    Given a session "alpha" exists
    And session "alpha" is locked
    When I PUT the bytes 0x01 to the encrypted state of "alpha"
    Then the response status is 403

  Scenario: GET state on missing session is 404
    When I GET the encrypted state of session id "00000000-0000-0000-0000-000000000000"
    Then the response status is 404
