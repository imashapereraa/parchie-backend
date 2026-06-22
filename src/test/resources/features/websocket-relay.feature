Feature: WebSocket relay
  Binary frames are broadcast to all peers in the same room except the sender,
  and never leak across rooms.

  Scenario: Frame is relayed to the other peer in the same session
    Given a session "alpha" exists
    And client "A" connects to "alpha"
    And client "B" connects to "alpha"
    When client "A" sends bytes 0x10,0x20,0x30
    Then client "B" receives bytes 0x10,0x20,0x30 within 5 seconds
    And client "A" receives nothing within 500 ms

  Scenario: Frames do not leak across sessions
    Given a session "alpha" exists
    And a session "beta" exists
    And client "A" connects to "alpha"
    And client "B" connects to "alpha"
    And client "C" connects to "beta"
    When client "A" sends bytes 0xAA,0xBB
    Then client "B" receives bytes 0xAA,0xBB within 5 seconds
    And client "C" receives nothing within 500 ms
