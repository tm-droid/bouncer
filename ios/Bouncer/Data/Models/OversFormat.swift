import Foundation

/// Cricbuzz reports a completed over as "19.6"; show it as "20". Port of Android `displayOvers`.
/// Anything that isn't `<whole>.<ball>` is returned unchanged.
func displayOvers(_ overs: String) -> String {
    let trimmed = overs.trimmingCharacters(in: .whitespaces)
    guard let match = trimmed.wholeMatch(of: #/(\d{1,4})\.(\d)/#) else { return overs }
    guard match.2 == "6", let whole = Int(match.1) else { return trimmed }
    return String(whole + 1)
}
