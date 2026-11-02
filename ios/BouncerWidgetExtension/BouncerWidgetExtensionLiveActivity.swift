//
//  BouncerWidgetExtensionLiveActivity.swift
//  BouncerWidgetExtension
//
//  Created by Tanush Malhotra (Personal) on 10/7/25.
//

import ActivityKit
import WidgetKit
import SwiftUI

struct BouncerWidgetExtensionAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        // Dynamic stateful properties about your activity go here!
        var emoji: String
    }

    // Fixed non-changing properties about your activity go here!
    var name: String
}

struct BouncerWidgetExtensionLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: BouncerWidgetExtensionAttributes.self) { context in
            // Lock screen/banner UI goes here
            VStack {
                Text("Hello \(context.state.emoji)")
            }
            .activityBackgroundTint(Color.cyan)
            .activitySystemActionForegroundColor(Color.black)

        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded UI goes here.  Compose the expanded UI through
                // various regions, like leading/trailing/center/bottom
                DynamicIslandExpandedRegion(.leading) {
                    Text("Leading")
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("Trailing")
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Bottom \(context.state.emoji)")
                    // more content
                }
            } compactLeading: {
                Text("L")
            } compactTrailing: {
                Text("T \(context.state.emoji)")
            } minimal: {
                Text(context.state.emoji)
            }
            .widgetURL(URL(string: "http://www.apple.com"))
            .keylineTint(Color.red)
        }
    }
}

extension BouncerWidgetExtensionAttributes {
    fileprivate static var preview: BouncerWidgetExtensionAttributes {
        BouncerWidgetExtensionAttributes(name: "World")
    }
}

extension BouncerWidgetExtensionAttributes.ContentState {
    fileprivate static var smiley: BouncerWidgetExtensionAttributes.ContentState {
        BouncerWidgetExtensionAttributes.ContentState(emoji: "😀")
     }
     
     fileprivate static var starEyes: BouncerWidgetExtensionAttributes.ContentState {
         BouncerWidgetExtensionAttributes.ContentState(emoji: "🤩")
     }
}

#Preview("Notification", as: .content, using: BouncerWidgetExtensionAttributes.preview) {
   BouncerWidgetExtensionLiveActivity()
} contentStates: {
    BouncerWidgetExtensionAttributes.ContentState.smiley
    BouncerWidgetExtensionAttributes.ContentState.starEyes
}
