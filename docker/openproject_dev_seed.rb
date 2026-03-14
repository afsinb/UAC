login = ENV.fetch("OPENPROJECT_DEV_LOGIN", "UAC")
password = ENV.fetch("OPENPROJECT_DEV_PASSWORD", "uac123")
firstname = ENV.fetch("OPENPROJECT_DEV_FIRSTNAME", login)
lastname = ENV.fetch("OPENPROJECT_DEV_LASTNAME", "Local")
mail = ENV.fetch("OPENPROJECT_DEV_EMAIL", "uac@local.test")
admin = ENV.fetch("OPENPROJECT_DEV_ADMIN", "true") == "true"

Setting.password_min_length = [password.length, 6].min if Setting.respond_to?(:password_min_length=)
Setting.password_min_adhered_rules = 0 if Setting.respond_to?(:password_min_adhered_rules=)

user = User.find_by(login: login) || User.new(
  login: login,
  firstname: firstname,
  lastname: lastname,
  mail: mail,
  admin: admin,
  status: "active"
)

user.firstname = firstname
user.lastname = lastname
user.mail = mail
user.password = password
user.password_confirmation = password
user.language = "en" if user.respond_to?(:language=)
user.force_password_change = false if user.respond_to?(:force_password_change=)
user.must_change_password = false if user.respond_to?(:must_change_password=)
user.mail_notification = "only_my_events" if user.respond_to?(:mail_notification=)
user.admin = admin
user.status = "active" if user.respond_to?(:status=)
user.save!

Token::API.where(user: user).delete_all
plain = Token::API.create_and_return_value(user)

puts "LOGIN=#{user.login}"
puts "PASSWORD=#{password}"
puts "TOKEN=#{plain}"

