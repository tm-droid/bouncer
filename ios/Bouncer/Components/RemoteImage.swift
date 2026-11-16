import SwiftUI
import UIKit

/// Decoded images kept in memory so list rows don't flash or refetch while scrolling; the bytes
/// themselves go through `URLCache.shared`.
enum RemoteImageCache {
    nonisolated(unsafe) static let memory: NSCache<NSURL, UIImage> = {
        let cache = NSCache<NSURL, UIImage>()
        cache.countLimit = 300
        return cache
    }()

    static func clear() { memory.removeAllObjects() }
}

/// A fill-scaled remote image; shows nothing until loaded and stays empty on failure, so the
/// caller's placeholder shows through.
struct RemoteImage: View {
    let url: URL
    @State private var image: UIImage?

    init(url: URL) {
        self.url = url
        _image = State(initialValue: RemoteImageCache.memory.object(forKey: url as NSURL))
    }

    var body: some View {
        ZStack {
            if let image { Image(uiImage: image).resizable().scaledToFill() }
        }
        .task(id: url) { await load() }
    }

    private func load() async {
        if let hit = RemoteImageCache.memory.object(forKey: url as NSURL) { image = hit; return }
        image = nil
        guard let (data, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let decoded = UIImage(data: data) else { return }
        let prepared = await decoded.byPreparingForDisplay() ?? decoded
        RemoteImageCache.memory.setObject(prepared, forKey: url as NSURL)
        guard !Task.isCancelled else { return }
        image = prepared
    }
}
