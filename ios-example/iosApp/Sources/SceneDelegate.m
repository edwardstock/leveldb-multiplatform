#import "SceneDelegate.h"
#import <LevelDBExample/LevelDBExample.h>

@implementation SceneDelegate

- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session options:(UISceneConnectionOptions *)connectionOptions {
    if (![scene isKindOfClass:[UIWindowScene class]]) {
        return;
    }
    UIWindowScene *windowScene = (UIWindowScene *)scene;
    self.window = [[UIWindow alloc] initWithWindowScene:windowScene];

    LDBELevelDbEntry *entry = [[LDBELevelDbEntry alloc] init];
    UIViewController *root = [entry rootViewController];
    self.window.rootViewController = root;
    [self.window makeKeyAndVisible];
}

@end
