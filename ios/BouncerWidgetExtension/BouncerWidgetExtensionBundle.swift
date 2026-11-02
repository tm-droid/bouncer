//
//  BouncerWidgetExtensionBundle.swift
//  BouncerWidgetExtension
//
//  Created by Tanush Malhotra (Personal) on 10/7/25.
//

import WidgetKit
import SwiftUI

@main
struct BouncerWidgetExtensionBundle: WidgetBundle {
    var body: some Widget {
        BouncerWidgetExtension()
        BouncerWidgetExtensionControl()
        BouncerWidgetExtensionLiveActivity()
    }
}
