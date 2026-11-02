import Foundation
@testable import Bouncer

private final class BundleToken {}

enum Fixture {
    /// Fixtures live in `BouncerTests/Fixtures`; a synchronized group may flatten them, so look in
    /// the subdirectory first and then the bundle root.
    static func data(_ name: String) throws -> Data {
        let bundle = Bundle(for: BundleToken.self)
        let ext = (name as NSString).pathExtension
        let base = (name as NSString).deletingPathExtension
        if let url = bundle.url(forResource: base, withExtension: ext, subdirectory: "Fixtures")
            ?? bundle.url(forResource: base, withExtension: ext) {
            return try Data(contentsOf: url)
        }
        throw NSError(domain: "Fixture", code: 1, userInfo: [NSLocalizedDescriptionKey: "missing fixture \(name)"])
    }

    static func string(_ name: String) throws -> String { String(decoding: try data(name), as: UTF8.self) }
}

/// Scripted `AppwriteAPI`: returns fixture bytes by key and counts calls.
actor FakeAPI: AppwriteAPI {
    var downloads: [String: Data] = [:]
    var rows: [String: Data] = [:]          // "table/id"
    var lists: [String: Data] = [:]         // table
    var executions: [String: Data] = [:]    // path
    var failure: AppwriteError?
    private(set) var downloadCalls: [String] = []
    private(set) var rowCalls: [String] = []
    private(set) var listCalls: [(table: String, queries: [String])] = []
    private(set) var executeCalls: [(path: String, body: String)] = []
    var delayNanos: UInt64 = 0

    func set(download file: String, _ data: Data) { downloads[file] = data }
    func set(row table: String, id: String, _ data: Data) { rows["\(table)/\(id)"] = data }
    func set(list table: String, _ data: Data) { lists[table] = data }
    func set(execution path: String, _ data: Data) { executions[path] = data }
    func set(failure: AppwriteError?) { self.failure = failure }
    func set(delay: UInt64) { delayNanos = delay }

    func download(file: String) async throws -> Data {
        downloadCalls.append(file)
        if delayNanos > 0 { try await Task.sleep(nanoseconds: delayNanos) }
        if let failure { throw failure }
        guard let d = downloads[file] else { throw AppwriteError.notFound }
        return d
    }
    func getRow(table: String, id: String) async throws -> Data {
        rowCalls.append("\(table)/\(id)")
        if let failure { throw failure }
        guard let d = rows["\(table)/\(id)"] else { throw AppwriteError.notFound }
        return d
    }
    func listRows(table: String, queries: [Query]) async throws -> Data {
        listCalls.append((table, queries.map(\.json)))
        if let failure { throw failure }
        return lists[table] ?? Data("{\"total\":0,\"rows\":[]}".utf8)
    }
    func execute(path: String, body: String) async throws -> Data {
        executeCalls.append((path, body))
        if let failure { throw failure }
        return executions[path] ?? Data("{}".utf8)
    }
}
