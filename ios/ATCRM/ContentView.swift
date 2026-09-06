import SwiftUI

struct ContentView: View {
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("AT CRM")
                    .font(.largeTitle.weight(.bold))
                Text("Исходники и обновления — в Git этого репозитория. Агент открывает ios/ATCRM.xcodeproj в Xcode, делает pull, правит, пушит.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                LabeledContent("API", value: Config.apiBase)
                LabeledContent("Версия", value: Config.marketingVersion)
            }
            .padding()
            .navigationTitle("AT CRM")
        }
    }
}

enum Config {
    static let apiBase = "https://crm.deliviotm.com/api"
    static let marketingVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.6.43"
}

#Preview {
    ContentView()
}
