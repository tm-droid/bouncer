struct Chip: View {
    let text: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(text)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(isSelected ? .blue.opacity(0.2) : .gray.opacity(0.2))
                )
                .foregroundColor(isSelected ? .blue : .gray)
        }
    }
}