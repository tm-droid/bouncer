import Testing
@testable import Bouncer

struct OversFormatTests {
    @Test(arguments: [
        ("19.6", "20"), ("49.6", "50"), ("0.6", "1"),
        ("12.3", "12.3"), (" 7.2 ", "7.2"), ("20", "20"), ("", ""), ("abc", "abc"),
    ])
    func displayOvers(input: String, expected: String) {
        #expect(Bouncer.displayOvers(input) == expected)
    }
}
