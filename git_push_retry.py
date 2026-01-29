import subprocess
import time
import sys
import datetime
import os
import platform

def run_command(cmd, cwd=None, check=True, show_output=True):
    """执行命令并返回结果"""
    print(f"执行命令: {' '.join(cmd)}")
    result = subprocess.run(
        cmd,
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding='utf-8',
        errors='ignore'
    )
    
    if show_output:
        if result.stdout:
            print(result.stdout)
        if result.stderr:
            print(result.stderr)
    
    if check and result.returncode != 0:
        raise subprocess.CalledProcessError(result.returncode, cmd, result.stdout, result.stderr)
    
    return result

def git_add_and_commit():
    """添加所有修改并提交"""
    print(f"\n[{datetime.datetime.now()}] ========== 步骤 1: Git 添加和提交 ==========")
    
    # 检查是否有修改
    status_result = run_command(["git", "status", "--porcelain"], check=False, show_output=False)
    if not status_result.stdout.strip():
        print("没有需要提交的修改。")
        return False
    
    print("检测到以下修改:")
    print(status_result.stdout)
    
    # 添加所有修改
    print("\n添加所有修改到暂存区...")
    run_command(["git", "add", "."])
    
    # 提交
    commit_message = input("\n请输入提交信息（直接回车使用默认信息）: ").strip()
    if not commit_message:
        commit_message = f"Update: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
    
    print(f"\n提交修改，信息: {commit_message}")
    run_command(["git", "commit", "-m", commit_message])
    
    print("✅ Git 提交成功！")
    return True

def build_apk():
    """编译打包 APK（本地编译，可选）"""
    print(f"\n[{datetime.datetime.now()}] ========== 步骤 2: 本地编译打包 APK（可选） ==========")
    
    # 检查 JAVA_HOME
    java_home = os.environ.get('JAVA_HOME')
    if not java_home:
        print("⚠️  未设置 JAVA_HOME，跳过本地编译")
        print("提示: 代码推送后，GitHub Actions 会自动编译 APK")
        return None  # 返回 None 表示跳过
    
    # 确定 gradlew 命令
    if platform.system() == "Windows":
        gradlew_cmd = "gradlew.bat"
    else:
        gradlew_cmd = "./gradlew"
    
    # 清理之前的构建
    print("清理之前的构建...")
    run_command([gradlew_cmd, "clean"], check=False)
    
    # 编译 Release APK
    print("\n开始编译 Release APK...")
    result = run_command([gradlew_cmd, "assembleRelease"], check=False)
    
    if result.returncode == 0:
        # 查找生成的 APK 文件
        apk_path = "app/build/outputs/apk/release/app-release.apk"
        if os.path.exists(apk_path):
            apk_size = os.path.getsize(apk_path) / (1024 * 1024)  # MB
            print(f"\n✅ APK 编译成功！")
            print(f"APK 路径: {os.path.abspath(apk_path)}")
            print(f"APK 大小: {apk_size:.2f} MB")
            
            # 复制到根目录（可选）
            root_apk = "XVirtualCamera.apk"
            try:
                import shutil
                shutil.copy2(apk_path, root_apk)
                print(f"已复制到根目录: {root_apk}")
            except Exception as e:
                print(f"复制到根目录失败: {e}")
            
            return True
        else:
            print(f"❌ 未找到生成的 APK 文件: {apk_path}")
            return False
    else:
        print(f"❌ APK 编译失败 (Exit Code: {result.returncode})")
        return False

def git_push_retry():
    """推送代码到 GitHub（带重试机制）"""
    print(f"\n[{datetime.datetime.now()}] ========== 步骤 3: 推送到 GitHub ==========")
    
    max_retries = 1000  # 设置一个很大的重试次数
    retry_interval = 5  # 重试间隔（秒）
    
    # 获取当前分支
    branch_result = run_command(["git", "rev-parse", "--abbrev-ref", "HEAD"], check=False, show_output=False)
    current_branch = branch_result.stdout.strip() or "master"
    print(f"当前分支: {current_branch}")

    count = 0
    while count < max_retries:
        count += 1
        try:
            print(f"\n[{datetime.datetime.now()}] 第 {count} 次尝试推送...")
            # 执行 git push 命令
            result = run_command(
                ["git", "push", "origin", current_branch],
                check=False,
                show_output=True
            )

            if result.returncode == 0:
                print(f"\n[{datetime.datetime.now()}] ✅ 推送成功！")
                return True
            else:
                print(f"[{datetime.datetime.now()}] ❌ 推送失败 (Exit Code: {result.returncode})")
                print(f"将在 {retry_interval} 秒后重试...")
                time.sleep(retry_interval)

        except KeyboardInterrupt:
            print("\n用户手动停止脚本。")
            return False
        except Exception as e:
            print(f"发生异常: {e}")
            time.sleep(retry_interval)

    print("达到最大重试次数，停止尝试。")
    return False

def main():
    """主函数"""
    print("=" * 60)
    print("XVirtualCamera - Git 推送和 APK 编译脚本")
    print("=" * 60)
    
    # 确保在正确的目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)
    print(f"工作目录: {os.getcwd()}")
    
    try:
        # 步骤 1: Git 添加和提交
        has_commit = git_add_and_commit()
        
        # 步骤 2: 编译 APK（可选，如果环境不支持会跳过）
        build_result = build_apk()
        
        # build_result 可能为 True（成功）、False（失败）、None（跳过）
        if build_result is False:
            response = input("\n本地 APK 编译失败，是否继续推送？（推送后 GitHub Actions 会自动编译）(y/n): ").strip().lower()
            if response != 'y':
                print("已取消推送。")
                return
        
        # 步骤 3: 推送到 GitHub（带重试）
        if has_commit:
            push_success = git_push_retry()
            if push_success:
                print("\n" + "=" * 60)
                print("✅ 代码推送成功！")
                print("📦 GitHub Actions 将自动编译 APK")
                print("   查看编译进度: https://github.com/[你的用户名]/XVirtualCamera/actions")
                print("=" * 60)
            else:
                print("\n" + "=" * 60)
                print("⚠️  推送失败")
                if build_result:
                    print("✅ 本地 APK 已编译完成")
                print("=" * 60)
        else:
            print("\n没有需要推送的提交。")
            if build_result:
                print("✅ 本地 APK 编译完成！")
        
    except KeyboardInterrupt:
        print("\n\n用户中断操作。")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ 发生错误: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
